"""Clause-aware chunking that preserves page and heading metadata."""

from uuid import uuid4

from app.infrastructure.terms.document_parser import PARSER_VERSION
from app.schemas.insurance_terms import ParsedBlock
from app.services.terms.tokenizer import TOKENIZER_VERSION, searchable_text

CHUNKER_VERSION = "clause-chunker-v1"
MAX_CHUNK_CHARS = 900


def build_chunks(
    blocks: list[ParsedBlock],
    *,
    product_id: int,
    profile_version_id: int,
    product_version: int,
    title: str,
) -> list[dict]:
    pieces: list[dict] = []
    for block in blocks:
        for part in _split(block.text):
            pieces.append(
                {
                    "text": part,
                    "heading_path": block.heading_path,
                    "clause_id": block.clause_id,
                    "clause_title": block.clause_title,
                    "page": block.page,
                    "block_type": block.block_type,
                }
            )

    merged: list[dict] = []
    for piece in pieces:
        if (
            merged
            and _can_merge(merged[-1], piece)
            and len(merged[-1]["text"]) + len(piece["text"]) + 1 <= MAX_CHUNK_CHARS
        ):
            merged[-1]["text"] += "\n" + piece["text"]
            pages = [page for page in (merged[-1].get("page_start"), piece.get("page")) if page]
            merged[-1]["page_start"] = min(pages) if pages else None
            merged[-1]["page_end"] = max(pages) if pages else None
        else:
            merged.append(
                {
                    **piece,
                    "page_start": piece["page"],
                    "page_end": piece["page"],
                }
            )

    chunks: list[dict] = []
    for index, piece in enumerate(merged):
        body = piece["text"].strip()
        if not body:
            continue
        embedding_text = " / ".join(piece["heading_path"] + [title, body])
        chunks.append(
            {
                "chunk_id": uuid4(),
                "product_id": product_id,
                "profile_version_id": profile_version_id,
                "product_version": product_version,
                "title": title,
                "clause_id": piece["clause_id"],
                "clause_title": piece["clause_title"],
                "heading_path": piece["heading_path"],
                "page_start": piece["page_start"],
                "page_end": piece["page_end"],
                "chunk_index": index,
                "text_content": body,
                "embedding_text": embedding_text,
                "search_tokens": searchable_text(" ".join(piece["heading_path"] + [body])),
                "parser_version": PARSER_VERSION,
                "chunker_version": CHUNKER_VERSION,
                "tokenizer_version": TOKENIZER_VERSION,
            }
        )
    return chunks


def _split(text: str) -> list[str]:
    normalized = "\n".join(line.strip() for line in text.splitlines() if line.strip())
    if len(normalized) <= MAX_CHUNK_CHARS:
        return [normalized] if normalized else []
    sentences: list[str] = []
    current = ""
    for part in normalized.replace("。", "。\n").replace("；", "；\n").splitlines():
        if not part:
            continue
        if len(part) > MAX_CHUNK_CHARS:
            if current:
                sentences.append(current)
                current = ""
            sentences.extend(
                part[start : start + MAX_CHUNK_CHARS]
                for start in range(0, len(part), MAX_CHUNK_CHARS)
            )
        elif current and len(current) + len(part) + 1 > MAX_CHUNK_CHARS:
            sentences.append(current)
            current = part
        else:
            current = f"{current}\n{part}" if current else part
    if current:
        sentences.append(current)
    return sentences


def _can_merge(left: dict, right: dict) -> bool:
    return (
        left.get("clause_id") == right.get("clause_id")
        and left.get("heading_path") == right.get("heading_path")
        and left.get("block_type") != "heading"
        and right.get("block_type") != "heading"
    )
