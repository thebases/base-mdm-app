# CSS / Less / Sass Review Guide

Review guidance for CSS and preprocessors with a focus on maintainability, responsive design, performance, and browser compatibility.

## Variables and Tokens

- Prefer design tokens or CSS variables over repeated hardcoded values.
- Use semantic names such as `--color-primary` or `--space-lg`.
- Keep token usage consistent across components.

## Selectors and Specificity

- Avoid overly specific selectors that are hard to override.
- Deep nesting in Sass often creates brittle selectors; keep nesting shallow.
- Prefer class-based styling over element chains and `!important`.

## Layout and Responsiveness

- Review whether layouts adapt cleanly to smaller screens and larger displays.
- Favor flexible units, container-aware sizing, and resilient wrapping behavior.
- Check that spacing, typography, and tap targets remain usable on mobile.

## Performance

- Expensive selectors, large box shadows, heavy filters, and frequent layout-triggering animations can hurt rendering.
- Prefer transforms and opacity for animation when possible.
- Ensure critical styles are not blocked by unnecessary weight or duplication.

## Maintainability

- Group related rules together and keep naming predictable.
- Watch for dead styles, duplicated declarations, and rules that fight each other.
- Component styles should have clear ownership boundaries.

## Browser Compatibility

- Validate newer features against the supported browser matrix.
- Use fallbacks or progressive enhancement where needed.
- Review vendor prefix strategy and autoprefixer coverage if relevant.

## Review Checklist

- [ ] Repeated values have been tokenized.
- [ ] Specificity stays manageable.
- [ ] Nesting is shallow and readable.
- [ ] Responsive behavior has been considered.
- [ ] Visual effects and animations are not excessively expensive.
- [ ] Styles remain compatible with supported browsers.
