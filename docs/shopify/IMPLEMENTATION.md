# Shopify backend implementation

Scope: this repository remains a Kotlin/Spring backend. The Shopify-facing frontend, App Bridge setup, Polaris UI and Shopify CLI app configuration belong in a separate project. No merchant UI is added here.

The first merchant workflow generates a 3D model from two images. Product selection and attachment are handled by the separate Shopify app using completed model outputs from this backend. Shopify App Pricing owns merchant plan selection. The separate frontend sends Shopify ID tokens to backend APIs and navigates merchants to hosted pricing.

Implementation slices:

1. Verify Shopify ID tokens; confirm an installation using online token exchange and the Admin API. Provision one workspace/connection/billing scope idempotently.
2. Read authoritative Partner API billing state, map configured offers, and keep store subscriptions/allowances independent. Reconcile without requiring subscription webhooks.
3. Expose store-scoped idempotent generation and model access APIs. Validate scope before every operation. Product listing, Shopify media uploads and product attachment are explicitly outside this repository.
4. Verify raw webhook HMAC, deduplicate uninstall/privacy events and handle store-scoped removal.
5. Characterize authentication, API mappings, isolation, quotas, retry behavior and webhook handling; run strict DSA checks/full build.
6. Document backend/frontend contracts and configuration. Live Shopify verification requires credentials and a development store supplied later.

Current status: backend implementation and automated tests are present. Live Shopify verification remains pending credentials and a development store. The merchant frontend and product attachment are outside this repository. Shopify is disabled by default.

Configure Shopify client ID/secret, app ID/handle, Partner organization/token and allowed frontend origins using the `shopify` properties in application.yml. Configure real Shopify offer handles in `plan_offers`; no production pricing handles are seeded. The frontend supplies a fresh Shopify ID token for each `/shopify/api` request, a UUID `Idempotency-Key` for generation, and redirects merchants to the pricing URL returned by the subscription endpoint. Register uninstall and mandatory privacy topics at `/shopify/webhooks`.

An accepted generation consumes one allowance, including jobs that later fail. Allowances currently follow the provider billing cycle; annual offers require a deliberate allowance policy before launch. Privacy cleanup deletes stored input objects and scoped database records. Generated outputs currently use the existing provider adapter; production output retention needs validation with that provider.
