"""PostgreSQL persistence shared by AgentOS and the insurance agent."""

from functools import lru_cache

from agno.db.postgres import PostgresDb
from sqlalchemy.engine import URL

from app.config import settings


@lru_cache(maxsize=1)
def get_postgres_db() -> PostgresDb:
    """Return the shared PostgresDb instance used for agent sessions and traces."""
    url = URL.create(
        drivername="postgresql+psycopg",
        username=settings.db_user,
        password=settings.db_password,
        host=settings.db_host,
        port=settings.db_port,
        database=settings.db_name,
    ).render_as_string(hide_password=False)
    return PostgresDb(id="bolee-agentos-db", db_url=url)


__all__ = ["get_postgres_db"]
