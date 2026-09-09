# Figure Panel Builder development

- Start by checking `git status` and the existing history. Preserve uncommitted work and make a baseline commit when needed before implementation. Commit changes in logical units. Never reset, revert, or delete existing work without authorization.
- Use incremental `build.ps1` verification. Avoid large deletion, replacement, or regeneration. Do not put junctions or symbolic links to shared dependencies inside `target` or other cleanup directories. Inspect exact resolved targets before any cleanup; Git cannot restore ignored outputs or external dependencies.
- Keep input TIFF files and the user's open Fiji images unchanged. Removal means removing a Condition or DisplayChannel from the figure configuration only.
- Test GUI changes in an isolated process with synthetic/test-data images. Do not close or restart the user's existing Fiji process or image windows.
- Preserve the settings JSON format and image-generation/export behavior unless the user asks for a change. Run relevant tests, record validation accurately, and commit the resulting code and documentation.
