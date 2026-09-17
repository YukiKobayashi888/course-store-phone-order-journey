package learning.storefront.orders;

import learning.storefront.infrai.IdentityAndSmsGateway;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CourseOrderJourney {
    private final IdentityAndSmsGateway gateway;
    private final Map<String, CourseOrder> orders = new LinkedHashMap<>();

    public CourseOrderJourney(IdentityAndSmsGateway gateway) {
        this.gateway = gateway;
    }

    public void beginPhoneSignup(String phone, String attemptId) {
        requirePhone(phone);
        requireText(attemptId, "attemptId");
        gateway.sendSignupCode(phone, "signup:" + attemptId);
    }

    public LoginResult finishPhoneLogin(String phone, String code, String attemptId) {
        requirePhone(phone);
        requireText(code, "code");
        IdentityAndSmsGateway.VerifiedIdentity identity =
                gateway.verifyPhone(phone, code, true, "verify:" + attemptId);
        IdentityAndSmsGateway.Session session =
                gateway.createSession(identity.userId(), "session:" + attemptId);
        return new LoginResult(identity.userId(), session.sessionId(), phone);
    }

    public CourseOrder checkout(LoginResult learner, String orderId, String courseTitle, int amountCents) {
        requireText(orderId, "orderId");
        requireText(courseTitle, "courseTitle");
        if (amountCents <= 0) throw new IllegalArgumentException("amountCents must be positive");
        CourseOrder order = new CourseOrder(orderId, learner.userId(), learner.phone(), courseTitle,
                amountCents, OrderState.CHECKED_OUT, null);
        if (orders.putIfAbsent(orderId, order) != null) throw new IllegalStateException("orderId already exists");
        return order;
    }

    public CourseOrder fulfill(String orderId) {
        CourseOrder order = order(orderId);
        if (order.state() != OrderState.CHECKED_OUT) throw new IllegalStateException("order is not ready to fulfill");
        CourseOrder fulfilled = order.withState(OrderState.FULFILLED);
        orders.put(orderId, fulfilled);
        return fulfilled;
    }

    public CourseOrder issueReceipt(String orderId) {
        CourseOrder order = order(orderId);
        if (order.state() != OrderState.FULFILLED) {
            throw new IllegalStateException("receipt requires a fulfilled order");
        }
        CourseOrder receipted = order.withReceipt("receipt-" + order.orderId());
        orders.put(orderId, receipted);
        return receipted;
    }

    public void sendCustomerUpdateCode(String orderId) {
        CourseOrder order = order(orderId);
        gateway.sendOrderUpdateCode(order.phone(), "order-update:" + order.orderId() + ":" + order.state());
    }

    public OrderUpdate openCustomerUpdate(String orderId, String code) {
        CourseOrder order = order(orderId);
        gateway.verifyPhone(order.phone(), code, true, "order-update-verify:" + order.orderId());
        return new OrderUpdate(order.orderId(), order.courseTitle(), order.state(), order.receiptId());
    }

    private CourseOrder order(String orderId) {
        CourseOrder order = orders.get(orderId);
        if (order == null) throw new IllegalArgumentException("unknown orderId");
        return order;
    }

    private static void requirePhone(String phone) {
        requireText(phone, "phone");
        if (!phone.startsWith("+") || phone.length() < 8) {
            throw new IllegalArgumentException("phone must use E.164 format");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }

    public enum OrderState { CHECKED_OUT, FULFILLED }

    public record LoginResult(String userId, String sessionId, String phone) {}

    public record OrderUpdate(String orderId, String courseTitle, OrderState state, String receiptId) {}

    public record CourseOrder(String orderId, String userId, String phone, String courseTitle,
                              int amountCents, OrderState state, String receiptId) {
        CourseOrder withState(OrderState next) {
            return new CourseOrder(orderId, userId, phone, courseTitle, amountCents, next, receiptId);
        }

        CourseOrder withReceipt(String nextReceiptId) {
            return new CourseOrder(orderId, userId, phone, courseTitle, amountCents, state, nextReceiptId);
        }
    }
}
