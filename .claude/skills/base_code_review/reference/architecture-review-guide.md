# Architecture Review Guide

Architecture review guide for evaluating whether the structure, boundaries, and abstractions in a codebase are appropriate.

## SOLID Principles Checklist

### Single Responsibility Principle

- Each class or module should have one clear reason to change.
- Watch for classes that are too large, too generic, or operate on unrelated data.
- Ask whether the class can be described in one short sentence.

### Open/Closed Principle

- New features should be added mainly through extension, not by repeatedly editing core logic.
- Large `if/else` or `switch` trees are common warning signs.
- If every new type requires touching many existing files, the design is probably too rigid.

### Liskov Substitution Principle

- Subtypes should be usable anywhere their parent type is expected.
- Empty overrides, `NotImplementedException`, and type checks against concrete subclasses are red flags.
- Ask whether callers would need to change if a subtype replaced the base type.

### Interface Segregation Principle

- Interfaces should stay focused and small.
- If implementations ignore methods or throw unsupported-operation errors, the interface is likely too broad.
- Split large interfaces into capability-specific contracts.

### Dependency Inversion Principle

- High-level code should depend on abstractions, not concrete infrastructure.
- Favor dependency injection over direct construction.
- Hardcoded connection strings, external clients, and concrete imports inside business logic are review warnings.

---

## Architecture Anti-Patterns

### Critical Anti-Patterns

- **Big Ball of Mud**: unclear boundaries and unrestricted cross-calls.
- **God Object**: one class owns too many responsibilities.
- **Spaghetti Code**: tangled control flow and deep nesting.
- **Lava Flow**: legacy areas nobody can safely change.

### Design Anti-Patterns

- **Golden Hammer**: the same pattern or tool applied everywhere.
- **Overengineering**: a simple problem solved with unnecessary abstractions.
- **Boat Anchor**: code kept only for a hypothetical future use.
- **Copy-Paste Programming**: duplicated logic across modules.

## Coupling and Cohesion

- Prefer message and data coupling over control, common, or content coupling.
- Aim for functional cohesion, where the parts of a module clearly support one task.
- Review how many dependencies a module has and how many other modules depend on it.
- High coupling and low cohesion usually point to poor boundaries.

## Layered Architecture Review

- Domain code should not depend directly on frameworks, databases, or transport layers.
- Application code should coordinate use cases, not become a thin wrapper around repositories.
- Controllers and UI adapters should stay lightweight and avoid business rules.
- Dependency direction should point inward toward the domain.

## Design Pattern Usage

- Use patterns to solve real extensibility or readability problems.
- Avoid patterns used only for style or imagined future complexity.
- If the abstraction adds more confusion than value, simplify it.

## Extensibility Review

- New features should not require invasive edits to central code.
- Look for extension points such as hooks, events, strategies, and configuration.
- Consider data growth, query patterns, caching, and horizontal scaling needs.

## Code Structure Practices

- Organize by feature or domain when possible.
- Use clear, intention-revealing names for classes, methods, and modules.
- Keep files, classes, and functions small enough to review comfortably.
- Prefer composition over inheritance when splitting responsibilities.

## Quick Review Checklist

- [ ] Dependencies point in the correct direction.
- [ ] Core business rules are decoupled from frameworks and persistence.
- [ ] No obvious God objects, circular dependencies, or hardcoded infrastructure details.
- [ ] Interfaces and abstractions are justified and focused.
- [ ] The design is reasonably extensible without overengineering.
