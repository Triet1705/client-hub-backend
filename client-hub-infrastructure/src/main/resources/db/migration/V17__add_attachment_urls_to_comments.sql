ALTER TABLE comments ADD COLUMN attachment_urls jsonb DEFAULT '[]'::jsonb;
