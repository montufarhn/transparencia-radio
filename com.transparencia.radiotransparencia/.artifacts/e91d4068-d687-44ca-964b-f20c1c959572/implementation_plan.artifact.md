# Implementation Plan - Fix warnings and errors in `activity_main.xml`

This plan aims to resolve various lint warnings and architectural errors in the main layout file of the application.

## User Review Required

> [!IMPORTANT]
> Some hardcoded strings will be moved to `strings.xml`. I'll use existing strings where possible or create new ones.

## Proposed Changes

### Resources

#### [MODIFY] [strings.xml](file:///C:/Users/montu/StudioProjects/com.la87hn.la87laprimera/app/src/main/res/values/strings.xml)
- Add missing string for "EN VIVO".

### Layouts

#### [MODIFY] [activity_main.xml](file:///C:/Users/montu/StudioProjects/com.la87hn.la87laprimera/app/src/main/res/layout/activity_main.xml)
- Change root `RelativeLayout` height to `match_parent`.
- Remove `android:orientation` from `RelativeLayout` elements (not supported).
- Remove `android:gravity` from `FrameLayout` (not supported).
- Replace deprecated `android:singleLine="true"` with `android:maxLines="1"`.
- Use `app:` namespace instead of `custom:` for `EqualizerView`.
- Extract hardcoded strings to `@string/` resources.
- Add `android:contentDescription="@null"` to decorative images to resolve accessibility warnings.
- Clean up redundant namespace declarations.

## Verification Plan

### Automated Tests
- Run `gradle_build` to ensure the project still compiles.
- Use `analyze_file` to verify that lint issues are resolved.

### Manual Verification
- Visual inspection of the layout (though I can't see it, I'll rely on semantic correctness).
