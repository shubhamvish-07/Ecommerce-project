# Free-tier deployment

This project can be deployed as a public GitHub Pages storefront and a Render
Spring Boot API backed by TiDB Cloud Starter (MySQL-compatible). Free hosting
has limitations: Render may spin the API down when idle, TiDB has usage quotas,
and Render's filesystem is temporary. Do not use this setup for production or
important customer data.

## 1. Publish the source repository

This project is intended for the public
[`shubhamvish-07/Ecommerce-project`](https://github.com/shubhamvish-07/Ecommerce-project)
repository. Review the files before publishing; never commit local
credentials, `.idea` files, database dumps, or customer data.

## 2. Create the database

Create a TiDB Cloud Starter cluster and database. Create a database user and
note its username, password, host, and database name. TiDB Starter requires
TLS. Set `DB_URL` in Render to a JDBC URL of the form:

`jdbc:mysql://HOST:4000/DATABASE?sslMode=VERIFY_IDENTITY`

Use the TiDB connection details verbatim for the host, database, and user
prefix. Add any certificate settings required by the current TiDB connection
instructions if the cluster does not accept the JVM default trust store.

## 3. Deploy the API

Create a Render Blueprint from the GitHub repository and select `render.yaml`.
Set the prompted values for `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and
`JWT_SECRET` in Render's secret environment variables. Generate a new,
high-entropy JWT secret; do not reuse a local value. Set
`CORS_ALLOWED_ORIGINS` to the GitHub Pages origin only, for example
`https://OWNER.github.io` (no repository path). Set the Cloudinary variables
`CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, and `CLOUDINARY_API_SECRET` in
Render as well; the API uses `IMAGE_STORAGE=cloudinary` from the Blueprint.

After the first deploy, copy the Render service's HTTPS URL. Sign up through
the site, then promote the intended administrator account using the TiDB
console with a carefully scoped update to that account's `users.role`.

## 4. Deploy the storefront

Enable GitHub Pages for the repository using **GitHub Actions** as the source.
In the repository's **Settings → Secrets and variables → Actions → Variables**,
add `API_BASE_URL` with the Render HTTPS URL (for example,
`https://ecommerce-api.onrender.com`). The Pages workflow deploys the
`ecommerce-frontend` directory and substitutes this API URL in its built files.

The published site URL is shown in the repository's **Settings → Pages** and
in the `github-pages` deployment environment after the workflow succeeds.

## Product image storage

The Render Blueprint stores uploaded product images in Cloudinary because
Render's filesystem is temporary. Set the Cloudinary cloud name, API key, and
API secret in Render's environment settings (keep the API secret private).
Optionally set `CLOUDINARY_FOLDER`; it defaults to `mul-and-warp/products`.
The admin product upload sends image files to Cloudinary and saves the returned
HTTPS image URL with the product. Local development defaults to disk storage.

Products created before Cloudinary was enabled may refer to local `/uploads/`
files that Render no longer has. Edit those products and upload their images
again; leaving the image field empty does not replace the old URL.

## Free-tier caveats

TiDB and Cloudinary's free plans have usage quotas. Product images already on a
local machine are not automatically copied to Cloudinary; upload them again
through the admin page.
