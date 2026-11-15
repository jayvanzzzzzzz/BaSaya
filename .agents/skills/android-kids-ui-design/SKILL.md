---
name: android-kids-ui-design
description: Build or redesign Android learning-app screens into visually exciting, memorable, polished experiences for children and early teens (especially Grade 7). Use when working on Kotlin Android XML/View or Compose frontends, dashboards, lessons, games, activities, quizzes, pronunciation, student navigation, UI themes, playful illustrations, interactive components, and animations. Prioritize bold child appeal, strong visual identity, light/dark support, real implementation, and avoidance of generic AI-generated card layouts.
---

# Android Kids UI Design — Playful Studio Edition

## Non-negotiable creative directive

**Make children WANT to tap and explore.** The user wants a frontend that is **catchy at first glance, joyful, colorful, interactive, clean, premium, and unmistakably designed for young learners**. This is **not** a minimal corporate dashboard with pastel icons. In the old version, restrained styling made the results too plain; **do not repeat that mistake**.

Target the excitement of an inviting learning game, with the fit and finish of a high-quality educational product. For Grade 7, make it vibrant and lively without babyish clip art, excessive decorations, or illegible text. **Playfulness should be visible in the first screenful, not hidden in hover states or a few accent-colored buttons.**

The result must feel **art-directed**, not like a generated template. Use a recognizable visual concept, a memorable focal illustration/graphic, expressive custom components, purposeful color, and animations that reward interaction. Do not stop at changing corner radii, margins, and button colors.

## First, understand the actual project

1. Inspect only the screen requested and its direct dependencies: XML layouts or Compose code, Fragment/Activity/adapter, theme and colors (including `values-night`), fonts, drawables, and existing assets.
2. Keep the existing frontend technology. **For an XML/View Kotlin app, design with XML + Kotlin; never migrate to Compose just to restyle it.**
3. Identify the student's age group, the screen's actual actions, the currently used theme, and any visual identity already established. Preserve existing naming and working user flows.
4. Pick **one strong creative direction** from `references/visual-directions.md` or create a compatible original one. Do not combine all visual themes randomly.
5. State a short internal design plan (hero, distinctive components, palette, animation, hierarchy) and **implement it**, not just describe it.

## The five required ingredients of a successful redesign

Every substantial screen redesign must include **all five** unless the screen's function genuinely forbids one:

1. **A showpiece moment:** A custom-designed hero, learning progress path, illustrated heading, expressive progress module, achievement area, or game-stage focal point. Not a plain title over a blank background.
2. **Playful shape language:** Creative but controlled silhouettes—offset layers, cut-paper shapes, rounded/notched tabs, oversized numerals, sticker accents, irregular section composition, or a themed track. Maintain a consistent visual vocabulary.
3. **Purposeful, punchy color:** Rich accent combinations with adequate text contrast and calm neutral reading areas. **Avoid an all-white screen with five faint pastel cards.**
4. **Alive interactions:** Press feedback, spring-like but short selection, progress reveals, answer responses, or a brief delightful success state. Animation should reinforce meaning, not delay use.
5. **Distinct component design:** At least two screen-specific components designed for their function. **Do not use one identical `MaterialCardView` pattern for every module**.

If the result still reads as “title + 5 identical rounded cards + floating decorations,” **redesign it again**.

## Art direction: designed for children's eyes

- Prefer **expressive graphic design** over generic UI kits: big color-blocked headlines, playful typography weights, colorful illustrated pathways, layered panels, friendly graphical cues, and one memorable brand motif.
- Mix energetic moments with breathable information zones. Let bright panels and quieter surfaces alternate. The lesson content must stay easy to read.
- Make progress tangible: stepping-stones, level paths, milestone stamps, streak trails, stars, ribbons, or small earned badges with meaningful states (not fake rewards).
- Make icons special: cohesive vector illustrations or custom-shaped icon holders, with consistent stroke, angle, silhouette, and detail. Use existing branded assets first; if needed, create original Android vector drawables using in-project XML resources. Avoid emoji as production UI icons, mismatched icon packs, and unlicensed characters.
- Give each learning category a recognizable identity through **color + icon + shape/pattern**, not color alone. Keep those identities consistent throughout the app.
- Add depth with layered shapes, borders, soft tactile shadows, offset backing plates, and occasional texture-like lines. **Avoid glassmorphism, random gradients, neon overload, ubiquitous blobs, and generic AI “soft cards.”**
- Layouts should have visual rhythm: a surprising large module, medium supporting content, and compact secondary actions. Avoid uniform stacks of equal-height cards.
- Make dark mode look **purposefully nighttime playful**, with luminous but controlled accents and dark layered surfaces—not inverted daylight colors.

## Energy, color, typography, and spacing

- Treat existing brand colors/fonts as the starting point. If no clear system exists, use the **Bright Learning Studio** direction in `references/visual-directions.md` as a *starting palette*, not an excuse to sprinkle every color everywhere.
- Use **one dominant signature color**, **two complementary playful accents**, and category/semantic colors for selected learning tasks. Deliberately contrast saturated action areas with warm or cool neutral content backgrounds.
- Never put low-contrast white lettering on light yellow, cyan, or pink. Validate contrast: aim for >=4.5:1 for normal text and >=3:1 for large text.
- Typography should carry personality. For existing Quicksand/Lexend assets, use expressive **weight/size pairing**: bold friendly headings, strong level numbers, clean readable body, and compact punchy labels. Do not add fonts or dependencies unless explicitly authorized.
- Keep spatial rhythm (e.g. 4/8/12/16/24/32 dp), but vary **composition and scale**, not random padding. Do not shrink learning content for decoration.
- Maintain >=48dp touch areas, readable Filipino/English labels, scalable text, legible selected states, and intuitive bottom navigation.

## Screen-specific expectations

- **Student dashboard:** Should feel like an adventure invitation. Design a **colorful learning hero** with an actual next step and meaningful progress, then present Lecture, Game, Activity, Pronunciation, Quiz with *distinct-but-related* illustrated module treatments. Use a varied composition rather than five matching cards. A small mascot/character motif is welcome **only if assets or lightweight original vector art allow it**. Show progress and completion badges accurately.
- **Lecture:** Make the lesson header feel like a colorful book chapter or collectible learning page. Keep reading area calm; illustrate section transitions and page milestones. Keep audio play, previous/next, and page indicators obvious.
- **Quiz:** Let question index, option selection, correctness, and progress feel like a friendly challenge. Options are generous touch panels with clear pressed/selected/focus states. Use small success celebrations for actual correct answers; wrong answers should be encouraging and readable.
- **Game:** Use purposeful play-field design. Make the game itself the visual star, with a clear score/progress area and unobstructed controls.
- **Activity:** Visually differentiate instructions, interactive content, and completion using themed tabs/stickers or lightweight paper-like panels; still keep the task central.
- **Pronunciation:** Feature a fun microphone/listening focal UI, clear recording/listening state, and honest network/offline limitations. Visualize actual interaction state, not fake audio waves.
- **Notifications and deadlines:** Keep hierarchy readable with friendly illustrations for empty states. Serious/overdue states should remain clear; avoid confetti or misleading celebratory styling.

Read `references/screen-recipes.md` for layout-specific implementation ideas. They are **inspirations, not boilerplate to paste everywhere**.

## Animation: delight without distraction

Build real UI state transitions with existing Android APIs instead of superficial “animation” claims.

- Use quick press-scale (e.g. ~0.96–0.98), elevation/outline changes, gentle bounce for a completed goal, short color/shape changes for answer states, and small staggered entrances only when appropriate.
- Typical motion: **120–280 ms** for press/selection; **300–500 ms** for occasional milestone animation. Never animate whole-screen content constantly; avoid indefinite bobbing/spinning and excessive confetti.
- Respect `Animator duration scale` and reduced-motion/accessibility settings where feasible. Use performant properties like translation/scale/alpha; avoid expensive redraw loops.
- Include no misleading progress animations or achievements disconnected from real data.

## Engineering guardrails

- **Preserve behavior:** Firebase data, grade/scoring logic, audio, deadlines, Room/database, networking, notification logic, authentication, view IDs, bindings, accessibility labels, navigation, adapters, and state semantics remain intact.
- Touch only requested UI and essential shared resources; do not refactor unrelated Kotlin or add a new UI framework. Ask only if there is a genuinely high-risk irreversible choice.
- Use local XML vector drawables, shape and state-list drawables, style/theme/color resources, and existing Material/View components. Implement custom lightweight views only if necessary for a meaningful visual outcome.
- Support existing light and dark themes (`values/`, `values-night/` where applicable), window insets, small screen sizes, long content, and large font scaling.
- No fake controls, dummy data, nonfunctional chips, or made-up progress numbers. UI visuals must reflect real underlying state.
- Keep resources reusable and named meaningfully; avoid hundreds of ad-hoc hardcoded hex values, giant nested layouts, and new libraries for simple effects.
- Run the smallest available build/resource validation after edits; check screen previews/simulator if available. Never claim a visual screenshot or build test occurred unless actually performed.

## Workflow that produces *visible* design improvement

1. **Audit:** Briefly identify why the target looks plain (e.g. homogeneous cards, weak color emphasis, no emotional focal point, lifeless state changes).
2. **Create one concept:** Give the design a working name (e.g. “Learning Quest”, “Sticker Notebook”, “Colorful Studio”). Choose a single composition system and mood, informed by the actual brand.
3. **Design a screenful:** Decide the hero showpiece, two custom components, category color/shape scheme, and at least two meaningful animation moments. Plan both themes.
4. **Build real resources and layout:** Don't just propose them. Edit XML/Kotlin, reusable drawable/style assets, and only necessary related resources.
5. **Critique against the anti-plain test below** and iterate if needed. Ensure hierarchy still works without decorations.
6. **Verify and report:** List concrete changed files, notable visuals, animation behavior, preservation of functionality, and actual checks run. Keep response short and implementation-focused.

## Mandatory anti-plain test

Before declaring success, verify all of these:

- [ ] The *first screenful* feels visibly joyful, colorful, and exciting to learners.
- [ ] There is a memorable hero or central graphic, not just a plain heading.
- [ ] There are **at least two deliberately different custom UI component treatments** fitting their purpose.
- [ ] All five learning modules are recognizable but not five clones of a rounded card.
- [ ] Playful colors/illustrations/shapes are designed as a cohesive system, not scattered decorations.
- [ ] At least two real interactions have state-aware motion/feedback when appropriate.
- [ ] Grade 7 students are respected: fun without babyish presentation or visual clutter.
- [ ] Theme, contrast, accessibility, text scaling, and real content remain usable.
- [ ] XML/Kotlin implementation is real and existing features still work.

**Fail condition:** If the only substantial difference is colors, corner radius, shadows, icon sizes, or card spacing, the task is NOT finished. Rework the layout composition and visual identity.

## Efficient Codex usage

For a single-screen request, inspect only that screen, one relevant logic file, shared styles/colors/drawables, and night-mode tokens. Make one cohesive implementation pass and one validation pass. Do not recursively read the whole repository or rewrite unrelated files. When uncertain, use existing UI conventions and make a sensible, creative decision instead of asking broad preference questions.
