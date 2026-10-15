# User Notification Service

`user-notification-service` runs on port 8082 and owns `user_db`. It keeps customer and staff profiles, staff-to-building assignments, in-app notifications, registered push-device tokens, and the processed-event ledger. It consumes the event topics in the system specification and creates profiles from `AccountRegistered`.

## Local startup

Copy `.env.example` to `.env`, set the required PostgreSQL passwords, then start the project with Docker Compose. The service waits for PostgreSQL, Kafka topic initialization, and Eureka. Flyway creates the schema on first startup. Only the API Gateway publishes the service APIs to clients.

## Optional email

Set `NOTIFICATION_EMAIL_ENABLED=true`, `NOTIFICATION_EMAIL_FROM`, and `MAIL_HOST`/`MAIL_PORT`. Set SMTP username/password and enable `MAIL_SMTP_AUTH` and `MAIL_SMTP_STARTTLS` when the SMTP server requires them. Email dispatch runs after the notification transaction commits; delivery failures are logged and do not remove the in-app notification.

## Optional Firebase Cloud Messaging

Set `NOTIFICATION_PUSH_ENABLED=true` and `FCM_PROJECT_ID`, then provide the Firebase service-account JSON through the protected `FCM_SERVICE_ACCOUNT_JSON` environment variable. If that variable is empty, the Firebase Admin SDK uses Google Application Default Credentials. Do not commit credentials to the repository. Push dispatch runs after commit and failures are logged independently of in-app storage.

## Event handling

The consumer uses `processed_event.event_id` for idempotency and writes each in-app notification in the same database transaction as its processed-event marker. Unknown event types are ignored. After three retries with one-second backoff, poison messages are routed to `<topic>.DLT` for inspection.

Notifications use concise Vietnamese titles and content based on the event payload. Recipient selection follows the recipient matrix in the shared system specification: building staff and admins, targeted customers/staff, or the tenants listed in a contract event.
