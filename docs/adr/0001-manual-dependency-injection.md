# Manual dependency injection

Gokapi is a single-module app with a small dependency graph. `GokapiApplication`
creates an `AppContainer` and passes its services to ViewModels and workers.
Constructor injection keeps HTTP clients replaceable in tests without adding
Hilt, generated code, or annotation processing. This follows Spendr ADR-0005.
