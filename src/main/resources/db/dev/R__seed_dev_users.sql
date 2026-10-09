INSERT INTO users (id, email, password_hash, full_name, role) VALUES
    ('11111111-1111-1111-1111-111111111111', 'owner@dev.local', 'dev-only', 'Dev Owner', 'VENUE_OWNER'),
    ('22222222-2222-2222-2222-222222222222', 'alice@dev.local', 'dev-only', 'Alice Customer', 'CUSTOMER'),
    ('33333333-3333-3333-3333-333333333333', 'bob@dev.local', 'dev-only', 'Bob Customer', 'CUSTOMER'),
    ('44444444-4444-4444-4444-444444444444', 'admin@dev.local', 'dev-only', 'Dev Admin', 'ADMIN')
ON CONFLICT (id) DO NOTHING;
