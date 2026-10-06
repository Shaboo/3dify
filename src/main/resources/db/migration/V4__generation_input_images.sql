-- Provider-independent: no upper bound on the number of stored views.
ALTER TABLE jobs ADD COLUMN input_images TEXT[];
UPDATE jobs SET input_images = ARRAY[input_image_1, input_image_2];
-- Nullable for compatibility with legacy writers; readers fall back to the named pair.
ALTER TABLE jobs ADD CONSTRAINT jobs_input_images_nonempty CHECK (input_images IS NULL OR cardinality(input_images) > 0);
