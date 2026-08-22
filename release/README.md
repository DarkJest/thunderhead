# Thunderhead release package

Publication-ready copy, capture plans and verification notes.

> The storefront copy, capture plans and media in this package were produced for v1.1.0 and
> have not been reshot since. The version facts below and the checksums are current; the
> screenshots, video scripts and gallery are not, and 1.3.0 added visible features they do not
> show. Treat the media as pending rather than as approved.

## Brand

- `branding/brand.md` — selected name, tagline, description, slug and alternatives
- `branding/brand.md` — icon and hero concept, palette and export checks; the final storefront hero
  must be a real in-game capture

## Storefronts

- `modrinth/description.md`, `short-description.txt`, `gallery-plan.md`
- `curseforge/description.md`, `short-description.txt`
- `github/README.md`

Replace every `{{..._URL}}` token after uploading the real media. Do not publish with placeholder links.

## Marketing production

- `marketing/screenshots.md` — seven-shot list with capture steps
- `marketing/showcase-world.md` — five prepared scene types
- `marketing/showcase-video.md` — 55-second script, OBS settings, camera and edit workflow
- `marketing/short-video.md` — 13-second vertical script and hooks
- `marketing/before-after.md` — fair Vanilla/Thunderhead comparison
- `marketing/feature-captions.md` — feature-card and gallery copy
- `marketing/reddit-posts.md` — titles and post bodies
- `marketing/20-minute-capture-run.md` — literal seven-shot field guide
- `marketing/media-checklist.md` — final release gate

## Prepared media

- `media/README.md` — selection decisions, upload mapping and missing shots
- `media/branding/` — CurseForge-ready project icon and original logo variants
- `media/gallery/` — selected and consistently named PNG files
- `media/video/` — clean 3.78-second 1080p60 loop
- `media/gif/` — high-quality and size-optimized GIF loops

## Release facts

- `FAQ.md` — public FAQ based on actual implementation
- `compatibility.md` — tested/untested source of truth
- `release-notes/1.3.0.md` — user-facing release notes for the current version
- `checksums.sha256` — checksums for the current successful build artifacts

Automated status for 1.3.0: `clean buildAll` passed, 226 tests passed, Fabric and NeoForge jars were produced. Manual in-game, Iris, OptiFine and dedicated-server validation remain explicitly separated in the checklist.

