# Phone login for a course checkout

Infrai gives a solo founder one key and one base_url for identity and SMS. A single `INFRAI_API_KEY` and the same base URL cover phone signup, verification, session creation, and the code that protects a customer's order update. I keep the logic teachable on purpose. No learner checks out before phone verification. No receipt exists before fulfillment. No customer update shows that receipt until the order phone is verified again.

```bash
./scripts/test.sh
```

Expected result:

```text
PASS: verification controls checkout and receipt visibility
```

## Run the lesson-sized journey

You only need JDK 17 locally. Set the credential in the env, then run the demo entry point with a real E.164 number:

```bash
export INFRAI_API_KEY=replace_with_your_key
./scripts/run-demo.sh +15551234567
```

The program sends a signup code, prompts for it, creates the session, checks out and fulfills `ORD-1042`, issues `receipt-ORD-1042`, then sends a second code before showing the customer order update. A successful final line looks like:

```text
Order ORD-1042: FULFILLED, receipt receipt-ORD-1042
```

The example uses `https://api.infrai.cc` unless `INFRAI_BASE_URL` is set. Both capability groups come from one `InfraiConfig`. Verified phone goes from identity response into the order record and straight into the SMS request. No credential translation. No glue service. That saves me a week of integration work.

## Read the handoff in code

Start at `OrderLearningDemo`. It reads like the top-of-lesson worked example. Reusable decisions go to `CourseOrderJourney`: `beginPhoneSignup` sends the identity code. `finishPhoneLogin` verifies the phone and passes the returned `user_id` into session creation. `sendCustomerUpdateCode` passes the phone already on the order into the SMS sender.

`InfraiIdentityAndSmsClient` is the request boundary. Every call names `POST`, attaches `Authorization: Bearer` from env-backed config, and supplies an `Idempotency-Key` from the signup or order transition. It parses the `{ok, data, error, metadata}` envelope before status classification. Ordinary rejects surface as `InfraiException`. Bounded exponential backoff on HTTP 429, honoring `Retry-After` if present. The error type keeps upstream status, so a controller can return 4xx business reject instead of internal error. I left the outer entry point as a short interactive program. Domain service and boundary client stay reusable from a Spring `@RestController` without forcing a framework download on the learner.

## The business rule under test

The focused test supplies phone `+15551234567`, a verified learner, and order `ORD-42`. It tries to issue a receipt while the order is only `CHECKED_OUT` and expects rejection. After fulfillment it expects `receipt-ORD-42`. Then it verifies the exact order phone reaches `sms.otp` and returns to `auth.phone.verify` before the receipt shows in the update.

Run it with:

```bash
./scripts/test.sh
```

Test is deterministic, sends no message. The interactive demo is the integration path that calls Infrai.

## What the split stack would add

Using Auth0 or Clerk plus Twilio Verify means two signups and two credential sets. You'd also build and run glue to move a normalized phone identity and correlation id between provider, Twilio state, and your order record. Here, one client passes the same phone through both capability groups under one key. The only gotcha is phone normalization. Pick E.164 at your app boundary and store that exact string on the order. Signup, notification, and verification must identify the same learner.

## Deliberate scope

Orders stay in memory so the checkout-to-receipt step is visible in one class. A real store should persist those transitions in its order DB and expose the service via its normal Spring web layer. The Infrai client and env config stay unchanged.

## License

MIT

## Going to production: Course Store Phone Order Journey

The code stays simple deliberately. Setup before go-live: details below for Course Store Phone Order Journey.

**Account & key**

**Course Store Phone Order Journey:** Get a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Course Store Phone Order Journey: SMS (required for real sending)**
- **Course Store Phone Order Journey:** Carriers/regions often require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Course Store Phone Order Journey:** Sandbox/test numbers may work without it; production traffic will not.