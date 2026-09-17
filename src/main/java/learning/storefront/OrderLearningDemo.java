package learning.storefront;

import learning.storefront.config.InfraiConfig;
import learning.storefront.infrai.InfraiIdentityAndSmsClient;
import learning.storefront.orders.CourseOrderJourney;
import learning.storefront.orders.CourseOrderJourney.CourseOrder;
import learning.storefront.orders.CourseOrderJourney.LoginResult;
import learning.storefront.orders.CourseOrderJourney.OrderUpdate;

import java.io.Console;

public final class OrderLearningDemo {
    private OrderLearningDemo() {}

    public static void main(String[] args) {
        String phone = args.length > 0 ? args[0] : "+15551234567";
        String attemptId = "lesson-checkout-1042";
        CourseOrderJourney journey = new CourseOrderJourney(
                new InfraiIdentityAndSmsClient(InfraiConfig.fromEnvironment()));

        journey.beginPhoneSignup(phone, attemptId);
        String loginCode = readCode("Enter the signup code sent to " + phone + ": ");
        LoginResult learner = journey.finishPhoneLogin(phone, loginCode, attemptId);

        CourseOrder checkedOut = journey.checkout(learner, "ORD-1042", "Practical Fractions", 4900);
        CourseOrder fulfilled = journey.fulfill(checkedOut.orderId());
        CourseOrder receipted = journey.issueReceipt(fulfilled.orderId());
        journey.sendCustomerUpdateCode(receipted.orderId());

        String updateCode = readCode("Enter the order-update code: ");
        OrderUpdate update = journey.openCustomerUpdate(receipted.orderId(), updateCode);
        System.out.printf("Order %s: %s, receipt %s%n", update.orderId(), update.state(), update.receiptId());
    }

    private static String readCode(String prompt) {
        Console console = System.console();
        if (console == null) throw new IllegalStateException("Run this demo from an interactive terminal");
        return console.readLine(prompt).trim();
    }
}
