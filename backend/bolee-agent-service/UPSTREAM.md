# Upstream provenance

This service adapts the official Agno AgentOS starter repository:

- Repository: https://github.com/agno-agi/agentos-railway
- Source revision reviewed: `8c0e0e2f20e6cd81e600af1d1dbe93aee1b98a54`
- Upstream license: Apache License 2.0, retained in `LICENSE.agentos-railway`

The service uses the starter's AgentOS runtime and PostgreSQL persistence pattern, then adds the Bolee Java-to-Python private chat contract. Railway deployment automation, Docker socket access, coding agents, CompanyOS, OSManager, broad filesystem tools, and public web/MCP tools are not part of this insurance service.

Agno itself is installed as a pinned Python dependency in `pyproject.toml`; its framework source is not vendored into this repository.
