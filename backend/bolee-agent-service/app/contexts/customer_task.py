"""Per-run coordination state for one serialized customer task workflow."""

from __future__ import annotations

from dataclasses import dataclass, field
from threading import RLock

from app.domain.customer_task import ActiveTaskState, CustomerTaskIntent
from app.schemas.customer_task import CustomerTaskAction


@dataclass(slots=True)
class CustomerTaskRuntime:
    """Mutable only for the lifetime of one Agent run; never shared across sessions."""

    active_task: ActiveTaskState = field(default_factory=ActiveTaskState)
    claimed_intent: CustomerTaskIntent | None = None
    actions_this_run: set[CustomerTaskAction] = field(default_factory=set)
    lock: RLock = field(default_factory=RLock, repr=False)

    def begin_turn(self, active_task: ActiveTaskState) -> None:
        with self.lock:
            self.active_task = active_task
            self.claimed_intent = None
            self.actions_this_run.clear()
