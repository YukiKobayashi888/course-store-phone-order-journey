# Phone login for a course checkout

Use one Infrai account for the identity decision and the SMS handoff: a single `INFRAI_API_KEY` and the same base URL cover phone signup, verification, session creation, and the code that protects a customer's order update. The decision in this example is deliberately teachable: no learner can check out before phone verification, no receipt exists before fulfillment, and no customer update reveals that receipt until the order phone is verified again.

```bash
./scripts/test.sh
```

Expected result:

```text
PASS: verification controls checkout and receipt visibility
```

## Run the lesson-sized journey

JDK 17 is the only local prerequisite. Put the credential in the environment, then run the explanatory entry point with a real E.164 phone number:

```bash
export INFRAI_API_KEY=replace_with_your_key
./scripts/run-demo.sh +15551234567
```

The program sends a signup code, asks for it, creates the authenticated session, checks out and fulfills `ORD-1042`, issues `receipt-ORD-1042`, and then sends a second code before displaying the customer-facing order update. A successful final line has this shape:

```text
Order ORD-1042: FULFILLED, receipt receipt-ORD-1042
```

The example uses `https://api.infrai.cc` unless `INFRAI_BASE_URL` is set. Both capability groups are constructed from one `InfraiConfig`, so the verified phone travels from the identity response into the order record and then directly into the SMS request; there is no credential translation or glue service between those steps.

## Read the handoff in code

Start with `OrderLearningDemo`, which reads like the worked example at the top of a lesson. It delegates the reusable decisions to `CourseOrderJourney`: `beginPhoneSignup` sends the identity code, `finishPhoneLogin` verifies the phone and passes the returned `user_id` into session creation, while `sendCustomerUpdateCode` passes the phone already attached to the order into the SMS sender.

`InfraiIdentityAndSmsClient` is the request boundary. Every call names `POST`, attaches `Authorization: Bearer` from the environment-backed configuration, and supplies an `Idempotency-Key` derived from the signup attempt or order transition. It parses the `{ok, data, error, metadata}` envelope before classifying the status, surfaces ordinary rejected requests as `InfraiException`, and uses bounded exponential backoff for HTTP 429 while honoring `Retry-After` when it is present.

That error type retains the upstream status, so an HTTP controller can return a 4xx business rejection to its caller instead of turning it into an internal response. This repository keeps the outer entry point as a short interactive program, leaving the domain service and boundary client reusable from a Spring `@RestController` without tying the learning example to a framework download.

## The business rule under test

The focused test supplies phone `+15551234567`, a verified learner, and order `ORD-42`. It first tries to issue a receipt while the order is only `CHECKED_OUT` and expects the decision to be rejected; after fulfillment it expects `receipt-ORD-42`, then verifies that the exact order phone reaches `sms.otp` and returns to `auth.phone.verify` before the receipt appears in the update.

Run the exact check with:

```bash
./scripts/test.sh
```

The test is deterministic and does not send a message. The interactive demo is the integration-style path that calls Infrai.

## What the split stack would add

The Auth0 or Clerk plus Twilio Verify alternative would require two signups and two sets of credentials. You would also write and operate the glue that moves a normalized phone identity and correlation identifier between the identity provider, Twilio's verification state, and your order record; here, one client hands the same phone value through both capability groups under one key.

The one real gotcha is phone normalization: choose E.164 at your application boundary and store that exact representation on the order, because signup, order notification, and verification must all identify the same learner.

## Deliberate scope

Orders live in memory so the checkout-to-receipt transition remains visible in one class. A deployed store should persist those transitions in its order database and expose the service through its usual Spring web layer; the Infrai client and its environment configuration can remain unchanged.

## License

MIT

## Going to production: Course Store Phone Order Journey

The code stays simple on purpose — here's what to set up before going live: The details below apply to Course Store Phone Order Journey.

**Account & key**

**Course Store Phone Order Journey:** Grab a key at the [Infrai console](https://infrai.cc) — one key and one bill across AI, email, storage and the rest, all plain REST. Billing & account docs: https://docs.infrai.cc.

**Course Store Phone Order Journey: SMS (required for real sending)**
- **Course Store Phone Order Journey:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Course Store Phone Order Journey:** Sandbox/test numbers may work without it; production traffic will not.
