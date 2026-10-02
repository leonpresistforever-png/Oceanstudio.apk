---
id: ux-design
name: Design & UX
description: Visual hierarchy, touch target ergonomics, accessibility, and Ocean monochrome design system implementation.
version: 2.0.0
required_tools:
  - view_file
  - replace_file_content
  - write_to_file
  - run_command
optional_tools:
  - generate_image
---

# Design & UX

## 1. Mission and Scope
Enforce high visual clarity, ergonomic touch targets, and strict compliance with the Ocean monochrome design language. Design & UX eliminates generic Material green/teal text dialogs, thick borders, and inconsistent paddings in favor of rounded bottom sheets, subtle drag handles, and high-contrast typography.

## 2. When to Invoke / When NOT to Invoke
- **Invoke When**:
  - Designing or refining user flows, modals, bottom sheets, or activity layouts.
  - Auditing touch targets, typography contrast, or component density for mobile ergonomics.
  - Replacing standard platform dialogs with Ocean monochrome modal sheets.
- **Do NOT Invoke When**:
  - Implementing backend network protocols or daemon lifecycle (use Deep Coding or Terminal & Runtime).
  - Building low-level CLI utilities with no graphical presentation.

## 3. Inputs to Gather
1. Target activity layout files (`activity_*.xml`, `bottom_sheet_*.xml`).
2. Current color palette definitions (`res/values/colors.xml`, `res/values/styles.xml`).
3. Screenshot or layout hierarchy of the screen under evaluation.
4. User interactions and primary vs secondary action priorities.

## 4. Tool Policy for This Domain
- Inspect layout XML with `view_file` to verify DP measurements and color tokens.
- Apply surgical edits using `replace_file_content` to layout structures and custom view adapters.
- Validate on-device or with viewport rendering before approving design changes.

## 5. Step-by-Step Operating Procedure
1. **Audit Hierarchy**: Identify the primary user goal on the screen. Ensure primary action is unmistakable and singular.
2. **Apply Ocean Monochrome Palette**:
   - Background: Pure White (`#FFFFFF`) or Off-White surface (`#FAFAFA` / `#F5F5F4`).
   - Primary Text: High-contrast Dark Ink (`#191817` or `#18181B`).
   - Secondary / Helper Text: Muted Charcoal (`#77736E` or `#71717A`).
   - Borders: Subtle hairline border (`#D8D8D8` or `#E7E5E4`), never heavy black outlines.
3. **Ergonomic Touch Targets**: Guarantee all interactive elements have a minimum bounding box of 48dp x 48dp.
4. **Modal Bottom Sheets**:
   - Replace square `AlertDialog` dialogs with rounded bottom sheets (top corners radius 20dp+).
   - Include a subtle horizontal drag handle (width 40-52dp, height 4dp, centered, muted grey).
   - Primary button: Solid black fill (`#191817`), white text (`#FFFFFF`), rounded corners (8-12dp).
   - Secondary button: Soft grey surface (`#F4F4F5`) with subtle border, dark ink text.
   - Inline errors: Subtle light-red background with dark red text directly below affected inputs.
   - Details: Hide advanced diagnostics behind an expandable "Details" disclosure toggle.

## 6. Domain-Specific Heuristics and Algorithms
- **Visual Weight Rule**: Only one primary filled black button per viewport level; all other actions must be secondary or tertiary outlines.
- **No Color Confusion**: Never use green or teal text for system actions. Reserve color strictly for status indicators (amber for rate-limiting, subtle red for auth errors).
- **Proportional Spacing Grid**: Use an 8dp baseline grid (8dp, 12dp, 16dp, 20dp, 24dp) for consistent rhythm.

## 7. Evidence Requirements
- XML diff confirming updated color attributes, corner radiuses, and padding dimensions.
- Touch target measurement verification (bounds >= 48dp).
- Absence of default platform dialog styling in user-facing connection sheets.

## 8. Failure Modes and Recovery
- *Cramped Touch Targets*: Increase padding or set `minHeight="48dp"` and `minWidth="48dp"`.
- *Low Contrast Text*: Test contrast ratios against WCAG 2.1 AA standards (minimum 4.5:1 for body copy).
- *Cluttered Modals*: Move secondary configurations into a dedicated "Advanced" sheet or collapsible disclosure.

## 9. Security and Permission Boundaries
- Respect `WindowManager.LayoutParams.FLAG_SECURE` where private data or credential input is present.
- Never render unmasked credentials or raw OAuth bearer tokens on screen.

## 10. Acceptance Tests
1. Interactive buttons meet or exceed 48dp touch target guidelines.
2. Modal dialogs use Ocean monochrome bottom sheets with drag handles and rounded corners.
3. Color palette strictly complies with Ocean greyscale specification with zero teal/green text actions.

## 11. Handoff Format
- **Design Overview**: Summary of layout hierarchy, spacing improvements, and component adaptations.
- **Tokens Applied**: Exact color and dimension tokens modified.
- **Verification**: Visual inspection confirmation and touch-target audit results.

## 12. Small Worked Examples
- *Example*: Converting a generic `AlertDialog` for Provider Connection into an `OceanBottomSheet` featuring rounded corners, centered drag handle, black primary "Direct Connect" button, and expandable technical details.
