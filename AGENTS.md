# Orchestration Rules for AI Coding Agents (Claude, Cursor, Antigravity)

1. **Stepwise Execution:** Run Phase operations sequentially. NEVER jump from Phase 1 straight to Phase 3. Complete and verify scaffolding before logic implementations.
2. **API Contract Enforcement:** 
    - The repository uses `packages/openapi/api-spec.yaml` as the absolute source of truth. 
    - When adding new domain logic, update the spec *first*, run linters, and utilize generated OpenApi models in the client front-end.
3. **Database Rules:** 
    - Add a new `V1__...sql` script in Flyway directories instead of manually changing the Spring entity without syncing schema. 
    - No direct data manipulation across bounded contexts; use repository or service layers specifically isolated in modules (`@ApplicationModuleListener` in Spring Modulith).
4. **Testing Standards:** 
    - If modifying core domain logic (`Calculations`, `Borrowing`, `Pooling`), you must create or run the associated unit/integration tests (`Testcontainers`).
5. **UI Component Restraint:** 
    - Do not inject arbitrary color schemes. Adhere to the established `shadcn/ui` and `Tailwind CSS` globals. 
    - Implement React Server Components where heavy DOM manipulation isn't needed. Use standard App Router patterns.
