"""Bounded parsers for Java-accepted insurance term files."""

import re
import shutil
import subprocess
from pathlib import Path

from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph
from pypdf import PdfReader

from app.schemas.insurance_terms import ParsedBlock

PARSER_VERSION = "terms-parser-v1"
MAX_PAGES = 2000
MAX_TEXT_CHARS = 2_000_000
_CLAUSE = re.compile(
    r"^\s*(第[一二三四五六七八九十百千万0-9]+条|[（(]?[一二三四五六七八九十0-9]+[）).、])\s*(.*)$"
)


class PermanentDocumentError(ValueError):
    """The document cannot be indexed without changing the source file."""


class TransientDocumentError(RuntimeError):
    """The parser could not process the document due to a transient failure."""


class InsuranceTermDocumentParser:
    def parse(self, path: str, original_file_name: str) -> list[ParsedBlock]:
        suffix = Path(original_file_name).suffix.lower()
        if suffix in {".txt", ".md"}:
            return self._parse_text(Path(path).read_text(encoding="utf-8-sig"))
        if suffix == ".pdf":
            return self._parse_pdf(path)
        if suffix == ".docx":
            return self._parse_docx(path)
        if suffix == ".doc":
            return self._parse_legacy_doc(path)
        raise PermanentDocumentError("UNSUPPORTED_FILE_TYPE")

    def _parse_pdf(self, path: str) -> list[ParsedBlock]:
        try:
            reader = PdfReader(path, strict=False)
            if len(reader.pages) > MAX_PAGES:
                raise PermanentDocumentError("DOCUMENT_PAGE_LIMIT")
            blocks: list[ParsedBlock] = []
            for page_number, page in enumerate(reader.pages, start=1):
                text = page.extract_text() or ""
                if text.strip():
                    blocks.extend(self._parse_text(text, page=page_number))
            self._check_text_size(blocks)
            if not blocks:
                raise PermanentDocumentError("OCR_REQUIRED_OR_EMPTY_DOCUMENT")
            return blocks
        except PermanentDocumentError:
            raise
        except Exception as exc:
            raise TransientDocumentError("PDF_PARSE_FAILED") from exc

    def _parse_docx(self, path: str) -> list[ParsedBlock]:
        try:
            document = Document(path)
            blocks: list[ParsedBlock] = []
            heading_path: list[str] = []
            for element in document.element.body.iterchildren():
                if element.tag.endswith("}p"):
                    paragraph = Paragraph(element, document)
                    text = paragraph.text.strip()
                    if not text:
                        continue
                    style = paragraph.style.name if paragraph.style else ""
                    if style.lower().startswith("heading"):
                        try:
                            level = max(1, min(6, int(style.split()[-1])))
                        except (ValueError, IndexError):
                            level = 1
                        heading_path = heading_path[: level - 1]
                        heading_path.append(text[:255])
                        blocks.append(
                            ParsedBlock(
                                text=text,
                                heading_path=heading_path.copy(),
                                clause_id=None,
                                clause_title=text,
                                block_type="heading",
                            )
                        )
                    else:
                        blocks.append(self._block(text, heading_path, None, "paragraph"))
                elif element.tag.endswith("}tbl"):
                    table = Table(element, document)
                    rows = [
                        " | ".join(cell.text.strip() for cell in row.cells) for row in table.rows
                    ]
                    text = "\n".join(row for row in rows if row.strip())
                    if text:
                        blocks.append(self._block(text, heading_path, None, "table"))
            self._check_text_size(blocks)
            if not blocks:
                raise PermanentDocumentError("EMPTY_DOCUMENT")
            return blocks
        except PermanentDocumentError:
            raise
        except Exception as exc:
            raise TransientDocumentError("DOCX_PARSE_FAILED") from exc

    def _parse_legacy_doc(self, path: str) -> list[ParsedBlock]:
        executable = shutil.which("antiword")
        if not executable:
            raise PermanentDocumentError("LEGACY_DOC_CONVERTER_UNAVAILABLE")
        try:
            converted = subprocess.run(
                [executable, path],
                check=True,
                capture_output=True,
                timeout=60,
            )
        except subprocess.TimeoutExpired as exc:
            raise TransientDocumentError("LEGACY_DOC_CONVERSION_TIMEOUT") from exc
        except subprocess.CalledProcessError as exc:
            raise PermanentDocumentError("LEGACY_DOC_CONVERSION_FAILED") from exc
        if len(converted.stdout) > MAX_TEXT_CHARS * 4:
            raise PermanentDocumentError("DOCUMENT_TEXT_LIMIT")
        try:
            text = converted.stdout.decode("utf-8", errors="replace")
        except UnicodeDecodeError as exc:
            raise PermanentDocumentError("LEGACY_DOC_ENCODING_UNSUPPORTED") from exc
        pages = text.split("\f")
        blocks: list[ParsedBlock] = []
        for page_number, page_text in enumerate(pages, start=1):
            if page_text.strip():
                blocks.extend(self._parse_text(page_text, page=page_number))
        self._check_text_size(blocks)
        if not blocks:
            raise PermanentDocumentError("EMPTY_DOCUMENT")
        return blocks

    def _parse_text(self, text: str, page: int | None = None) -> list[ParsedBlock]:
        blocks: list[ParsedBlock] = []
        headings: list[str] = []
        for raw_line in text.splitlines():
            line = raw_line.strip()
            if not line:
                continue
            if line.startswith("#"):
                heading = line.lstrip("#").strip()
                headings = [heading]
                blocks.append(
                    ParsedBlock(
                        text=heading,
                        page=page,
                        heading_path=headings.copy(),
                        clause_title=heading,
                        block_type="heading",
                    )
                )
                continue
            blocks.append(self._block(line, headings, page, "paragraph"))
        self._check_text_size(blocks)
        if not blocks:
            raise PermanentDocumentError("EMPTY_DOCUMENT")
        return blocks

    def _block(
        self, text: str, headings: list[str], page: int | None, block_type: str
    ) -> ParsedBlock:
        match = _CLAUSE.match(text)
        clause_id = match.group(1).strip() if match else None
        clause_title = (match.group(2).strip() or text[:255]) if match else None
        return ParsedBlock(
            text=text,
            page=page,
            heading_path=headings.copy(),
            clause_id=clause_id,
            clause_title=clause_title,
            block_type=block_type,
        )

    @staticmethod
    def _check_text_size(blocks: list[ParsedBlock]) -> None:
        if sum(len(block.text) for block in blocks) > MAX_TEXT_CHARS:
            raise PermanentDocumentError("DOCUMENT_TEXT_LIMIT")
