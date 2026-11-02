# Microsoft SwiftKey v9.13.18.4 UI & Architecture Reference

Target Device: Google Pixel 9 (`52031VDAQ000YD`)
Package: `com.touchtype.swiftkey` (version `9.13.18.4`, versionCode `1236402240`)
Artifact SHA-256 (`base.apk`): `adca8b4fc4157acd3e68008a52ade1cc19abcd37a9dc0977cd3519aec794c0e5`

---

## 1. Writing Assistance Hub & Toolbar Panel Container

### Layouts & Classes
- `res/layout/toolbar_panel_layout.xml` (`0x7f0d010f`) -> `nd0/k1` (`ToolbarPanelLayout`)
- `res/layout/hub_view.xml` (`0x7f0d006c`) -> `pd0/j` (`WritingAssistanceHub`)
- `res/layout/hub_item.xml` (`0x7f0d006b`)
- `res/layout/toolbar_panel_bottom_bar.xml` (`0x7f0d010e`)

### Key UI Mechanics
1. **Top Navigation Bar (`hub_top_row`)**:
   - Left: Back button (`id/toolbar_panel_back`, `ic_chevron_left_small`, 1:1 aspect ratio).
   - Center: `HorizontalScrollView` (`id/hub_items_wrapper`, 16dp horizontal fading edge) containing `hub_item` tabs (`14sp` text, `8dp` horizontal padding, `1.5dp` bottom active indicator `id/hub_selected_border`).
   - Right: Optional outlined CTA pill (`id/action_button`, `maxWidth = 200dp`, auto-sized `8sp..14sp`).
2. **Bottom Navigation Bar (`toolbar_panel_bottom_bar.xml`)**:
   - Left: `"abc"` MaterialButton (`id/toolbar_panel_abc_button`, aspect ratio `1.25:1`, `16sp` text) that immediately closes the AI panel and restores QWERTY keys.
   - Center: `SwiftKeyTabLayout` (`id/toolbar_panel_tabs`) with `20dp` horizontal fading edge.
   - Right: Hold-to-repeat `DeleteKeyButton` (`id/toolbar_panel_delete_key`, 1:1 ratio).

---

## 2. Tone Changer (Horizontal Snap-Pager Carousel)

### Layouts & Classes
- `res/layout/toolbar_tone_change_panel.xml` (`0x7f0d0111`) -> `yd0/g`
- `res/layout/toolbar_tone_change_tone.xml` (`0x7f0d0112`) -> `yd0/a` (Adapter), `yd0/e` (ViewHolder)
- `yd0/b` (Auto-size text `OnLayoutChangeListener`)
- `yd0/c` (Accept/Replace `OnClickListener`)
- `yd0/j` (`ToolbarToneChangeViewModel`)

### Key Dimensions & Mechanics
- **Horizontal Snap Carousel**:
  - Uses horizontal `LinearLayoutManager` + `PagerSnapHelper` (`Lra/c1;`).
  - Card max width: `330dp` (`dimen/hub_card_max_width` = `0x7f070114`).
  - Recycler end padding: `32dp` (`dimen/tone_change_tone_recycler_padding` = `0x7f0704ec`) so the next card peeks from the right edge.
  - Card corner radius: `12dp` (`dimen/toolbar_panel_standard_corner_radius`), stroke width: `0.5dp` (`dimen/toolbar_panel_card_stroke_width`).
- **Card Internal Layout (`toolbar_tone_change_tone.xml`)**:
  - Top Tone Badge (`id/tone_change_tone_type`): `14sp`, pill background, `10dp` horizontal padding, `1dp` vertical padding.
  - Scrollable Body (`id/tone_change_card_scroll_view` -> `id/tone_change_tone_title`): Vertical `ScrollView` (`16dp` fading edge) with dynamic text sizing between `15sp` (`tone_change_tone_title_size_min`) and `20sp` (`tone_change_tone_title_size_max`) in `1sp` increments (`yd0/b`).
  - Bottom Action Row:
    - `id/tone_change_card_accept` ("Accept" / "Replace"): Replaces text in the host input field and transitions button state to `"Replaced"` (`ic_accept_and_replace_selected`, `yd0/d`).
    - `id/tone_change_card_copy` ("Copy"): Copies the toned text to the clipboard.
- **Cycling Loading Messages (`panel_loading_layout.xml`)**:
  - Rotates 3 status strings below the spinner:
    1. `"Analysing text…"` (`0x7f140adb`)
    2. `"Generating alternative versions…"` (`0x7f140adc`)
    3. `"Suggesting new tones…"` (`0x7f140add`)
- **Minimum Word Guard**:
  - Requires at least 3 words (`"Welcome to Tone!"` / `"Type at least three words to see rewrites"`, `0x7f140ae1`).

---

## 3. Editor / Grammar Improve Panel & Inline Critique Ribbon

### Layouts & Classes
- `res/layout/toolbar_improve_panel.xml` (`0x7f0d0109`) -> `sd0/b`, `sd0/e`
- `res/layout/editor_critique_ribbon_view.xml` (`0x7f0d0058`) -> `ta0/d`, `ta0/u`

### Key Mechanics
- **Editor Improve Panel (`toolbar_improve_panel.xml`)**:
  - Header label: `"AI generated"` (`0x7f140040`).
  - Card badge: `"Grammar, Spelling, Punctuation"` (`0x7f14025a`).
  - Bottom buttons: `Replace` (`id/improve_replace_button`) and `Copy` (`id/improve_copy_button`).
- **Inline Candidate-Bar Critique Chip (`editor_critique_ribbon_view.xml`)**:
  - Morphs the candidate strip (`background_fade_transition`) to show a single centered MaterialButton chip (`id/first_suggestion_chip`, `14dp` text, `16dp` icon, `14dp` horizontal inset, `maxEms=11`) for 1-tap grammar acceptance.

---

## 4. Smart Host Input Extraction & In-Place Replacement

### Classes & Methods
- `g60/f.p(boolean)` (`0x2cb344`):
  1. Calls `InputConnection.getSelectedText(0)`. If non-null, returns the user's highlighted selection.
  2. Otherwise calls `InputConnection.getExtractedText(ExtractedTextRequest(), 0).text` (or `getTextBeforeCursor(0x7fffffff, 0)`), returning the entire draft currently typed in the host app's text field.
- `cc0/n.K(zb0/k0, bc0/a)` (`0x1ed084`) & `zb0/x.n(String)` / `zb0/x.w(String)`:
  - When replacing text:
    - If text was selected in the host app, replaces only the selected span and updates selection to `(selectionStart, selectionStart + newText.length)`.
    - If no text was selected, calls `finishComposingText()`, selects the full extracted range `setSelection(0, extractedLength)`, commits the replacement text over it, and places the cursor at the end.

---

## 5. Gesture Ribbons & One-Tap Undo Chip

### Layouts & Classes
- `res/layout/cursor_control_overlay_view.xml` (`0x7f0d003c`) -> `tb0/b`
- `res/layout/quick_delete_ribbon_view.xml` (`0x7f0d00e9`) -> `cd0/h`, `cd0/e`, `cd0/g`

### Key Mechanics
- **Spacebar Cursor Control**: Displays `"Drag finger to move cursor"` (`0x7f14015e`) over the dimmed keyboard frame while sliding horizontally on the spacebar.
- **Quick Delete & Undo Chip**: After a bulk text deletion or replacement, displays an inline `"Undo"` chip (`id/quick_delete_undo_chip`, icon `ic_arrow_undo`) in the top candidate bar to restore the previous text in a single tap (`cd0/g` `onInsertDeletedText`).
