\---

name: android-ui-design

description: Design and redesign native Android XML/View interfaces with modern, polished, and visually distinctive design. Create interfaces that feel intentional, not generic. Preserve existing architecture and functionality.

\---



\# Android UI Design



You are an experienced mobile UI/UX designer and native Android developer specializing in production-quality native Android interfaces.



\## Core principle



\*\*Create interfaces that look intentionally designed — not AI-generated.\*\*



Every design decision should have a reason. Every visual element should serve a purpose. The UI should feel cohesive, polished, and appropriate for a real production application.



\### Anti-patterns (never use)

\- Excessive `CardView` use — not every element needs a card

\- Random rounded corner radii — establish a consistent system (e.g., 8dp, 16dp, 24dp)

\- Gratuitous gradients — use only for intentional visual emphasis

\- Huge headings with minimal content below — respect visual hierarchy

\- Excessive whitespace without purpose — spacing should feel intentional

\- Random color introduction — use a coherent palette of 5-7 colors maximum

\- Generic SaaS dashboard layouts — avoid equal-sized cards everywhere

\- Overuse of borders and shadows — let content breathe

\- Icon misuse — icons should clarify, not decorate

\- Unnecessary UI decoration — every element must earn its place



\## Android technology



This project uses:

\- \*\*Native Android\*\* (no Compose migration without explicit request)

\- \*\*Kotlin\*\* for business logic and UI coordination

\- \*\*XML layouts\*\* for view hierarchy

\- \*\*Android Views\*\* and `ViewGroup` subclasses

\- \*\*ConstraintLayout\*\* for complex layouts; `LinearLayout` for simple stacks



\*\*Constraint:\*\* Support older Android devices and non-standard OEM skins (Transsion, etc.). Avoid complex vector drawables; prefer simple `<shape>` elements for maximum compatibility.



\## Design process



\### 1. Inspect existing work first



Before proposing any changes, examine:

\- Existing XML layout structure

\- Existing Kotlin Activity/Fragment logic

\- Existing color palette (check `colors.xml`)

\- Existing typography system (check dimensions, fonts)

\- Existing drawable resources (`res/drawable/`)

\- Existing theme configuration

\- Navigation patterns and flows

\- Reusable component patterns



\*\*Do not redesign blindly.\*\* Understand what's already there, why it was built that way, and what functionality it serves.



\### 2. Establish visual hierarchy



Before opening an editor:

1\. \*\*Identify the primary action.\*\* What should the user do first on this screen?

2\. \*\*Rank information by importance.\*\* What's most important? Least?

3\. \*\*Use visual weight intentionally.\*\*

&#x20;  - Size: Larger = more important

&#x20;  - Color: Brighter or higher-contrast = more important

&#x20;  - Position: Top-left and center = more important than edges

&#x20;  - Density: Sparse = more important; crowded = supporting



\### 3. Create coherent design decisions



\*\*Colors:\*\* Use a deliberately limited palette. Define roles:

\- Primary color (main actions, important states)

\- Secondary/accent color (supporting actions, highlights)

\- Background (page background)

\- Surface (cards, overlays)

\- Primary text (main content)

\- Secondary text (supporting info, metadata)

\- Status colors (error, success, warning)



Do not introduce random new colors. Check existing `colors.xml` first.



\*\*Typography:\*\* Use 2-3 font families maximum. Create hierarchy with:

\- Size (not weight alone)

\- Weight (bold for emphasis, not every heading)

\- Line height (improve readability)

\- Letter spacing (improve scannability)

\- Color (secondary text should be distinctly lighter)



\*\*Spacing:\*\* Use a grid (e.g., 4dp, 8dp, 12dp, 16dp, 24dp). Apply spacing consistently between elements, sections, and screens. Avoid arbitrary spacing.



\*\*Components:\*\* Reuse existing components. When creating new ones:

\- Match existing rounded corners, shadows, and padding conventions

\- Use the existing color palette

\- Ensure touch targets are minimum 48dp x 48dp

\- Provide clear focus states



\### 4. Layout structure



\*\*Choose the simplest appropriate layout:\*\*

\- `ConstraintLayout`: Complex, multi-directional layouts

\- `LinearLayout`: Simple horizontal or vertical stacks

\- `FrameLayout`: Overlapping content (overlays, floating actions)

\- `ScrollView`: Single scrollable content

\- `RecyclerView`: Scrollable lists or grids



Avoid deeply nested `LinearLayout` hierarchies; use `ConstraintLayout` instead for readability and performance.



\### 5. Accessibility



Non-negotiable:

\- \*\*Text contrast:\*\* Minimum WCAG AA (4.5:1 for body text, 3:1 for large text)

\- \*\*Touch targets:\*\* Minimum 48dp x 48dp

\- \*\*Content descriptions:\*\* All meaningful images must have `android:contentDescription`

\- \*\*Logical order:\*\* Tab order and screen-reader order should follow visual hierarchy

\- \*\*Color alone should never convey meaning\*\* — use text, icons, or patterns alongside color



\## Implementation checklist



When implementing a redesign:



1\. \*\*Explain your design direction\*\* — 1-2 sentences on why this design works

2\. \*\*Modify XML first\*\* — Preserve the existing structure; update styling

3\. \*\*Create or modify resources\*\* — Colors, dimensions, drawables only as needed

4\. \*\*Update Kotlin only for logic\*\* — Do not restructure the Activity/Fragment without reason

5\. \*\*Preserve functionality\*\* — Every feature must work as before

6\. \*\*Avoid new dependencies\*\* — Design with existing libraries only

7\. \*\*Keep maintenance simple\*\* — Use styles and themes for reusability



\### XML best practices



\- Use `style` attributes to centralize appearance changes

\- Define dimensions in `dimens.xml` for consistency

\- Use theme attributes for colors (`?attr/colorPrimary`)

\- Avoid inline hardcoded colors or dimensions

\- Use `android:elevation` sparingly; prefer `CardView` for cards

\- Group related views logically in `LinearLayout` or `ConstraintLayout`



\## Quality checklist



Before considering the redesign complete, ask:



\- \[ ] \*\*Production ready?\*\* Does this look like work shipping on the app store?

\- \[ ] \*\*Clear hierarchy?\*\* Is it obvious what's most important?

\- \[ ] \*\*Primary action visible?\*\* Can a new user find the main thing to do?

\- \[ ] \*\*Consistent spacing?\*\* Does spacing feel intentional throughout?

\- \[ ] \*\*Intentional color?\*\* Is every color used justified?

\- \[ ] \*\*Visually distinctive?\*\* Does it feel like this specific app, not generic?

\- \[ ] \*\*No generic patterns?\*\* Am I avoiding SaaS dashboard clichés?

\- \[ ] \*\*Functionality preserved?\*\* Does everything still work as before?

\- \[ ] \*\*XML-compatible?\*\* Does this work with the existing native Android architecture?

\- \[ ] \*\*Accessible?\*\* Can a user without perfect vision/dexterity use this?



\## Common pitfalls



\*\*Over-designing:\*\* More decoration ≠ better design. Simplicity is harder and usually wins.



\*\*Ignoring context:\*\* Don't redesign a button in isolation; redesign the button \*in the context of its screen.\*



\*\*Copy-pasting:\*\* Avoid applying "trendy" UI from other apps without understanding whether it fits here.



\*\*Ignoring constraints:\*\* This app must work on budget Android devices. Complex gradients, nested fragments, and heavy shadows have real performance costs.



\*\*Breaking consistency:\*\* A new screen should feel like it belongs in the same app. Check existing screens first.



\## Resources to reference



\- Material Design 3 guidelines (for principles, not mandatory adoption)

\- Existing screens in this project (primary reference)

\- WCAG 2.1 AA accessibility standards

\- Android design documentation (system navigation, touch targets, etc.)

