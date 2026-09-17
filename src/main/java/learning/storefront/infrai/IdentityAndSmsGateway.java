package learning.storefront.infrai;

import java.util.Map;

public interface IdentityAndSmsGateway {
    void sendSignupCode(String phone, String requestId);
    VerifiedIdentity verifyPhone(String phone, String code, boolean login, String requestId);
    Session createSession(String userId, String requestId);
    void sendOrderUpdateCode(String phone, String requestId);

    record VerifiedIdentity(String userId, Map<String, Object> data) {}
    record Session(String sessionId, Map<String, Object> data) {}
}
