package learning.storefront.orders;

import learning.storefront.infrai.IdentityAndSmsGateway;
import learning.storefront.orders.CourseOrderJourney.CourseOrder;
import learning.storefront.orders.CourseOrderJourney.LoginResult;
import learning.storefront.orders.CourseOrderJourney.OrderState;
import learning.storefront.orders.CourseOrderJourney.OrderUpdate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CourseOrderJourneyTest {
    public static void main(String[] args) {
        FakeGateway gateway = new FakeGateway();
        CourseOrderJourney journey = new CourseOrderJourney(gateway);

        journey.beginPhoneSignup("+15551234567", "lesson-42");
        LoginResult login = journey.finishPhoneLogin("+15551234567", "246810", "lesson-42");
        CourseOrder order = journey.checkout(login, "ORD-42", "Geometry Foundations", 3200);

        assertThrows(() -> journey.issueReceipt(order.orderId()), "receipt requires fulfillment");
        journey.fulfill(order.orderId());
        CourseOrder receipted = journey.issueReceipt(order.orderId());
        journey.sendCustomerUpdateCode(order.orderId());
        OrderUpdate update = journey.openCustomerUpdate(order.orderId(), "135790");

        check(receipted.state() == OrderState.FULFILLED, "order should be fulfilled");
        check("receipt-ORD-42".equals(update.receiptId()), "verified update should include the receipt");
        check(gateway.calls.equals(List.of(
                "auth-send:+15551234567:signup:lesson-42",
                "auth-verify:+15551234567:246810:verify:lesson-42",
                "session:user-42:session:lesson-42",
                "sms-otp:+15551234567:order-update:ORD-42:FULFILLED",
                "auth-verify:+15551234567:135790:order-update-verify:ORD-42"
        )), "identity-to-SMS handoff should preserve phone and order context");

        System.out.println("PASS: verification controls checkout and receipt visibility");
    }

    private static void assertThrows(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("Expected failure: " + message);
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("fulfilled"), message);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeGateway implements IdentityAndSmsGateway {
        private final List<String> calls = new ArrayList<>();

        @Override
        public void sendSignupCode(String phone, String requestId) {
            calls.add("auth-send:" + phone + ":" + requestId);
        }

        @Override
        public VerifiedIdentity verifyPhone(String phone, String code, boolean login, String requestId) {
            calls.add("auth-verify:" + phone + ":" + code + ":" + requestId);
            return new VerifiedIdentity("user-42", Map.of("user_id", "user-42"));
        }

        @Override
        public Session createSession(String userId, String requestId) {
            calls.add("session:" + userId + ":" + requestId);
            return new Session("session-42", Map.of("session_id", "session-42"));
        }

        @Override
        public void sendOrderUpdateCode(String phone, String requestId) {
            calls.add("sms-otp:" + phone + ":" + requestId);
        }
    }
}
