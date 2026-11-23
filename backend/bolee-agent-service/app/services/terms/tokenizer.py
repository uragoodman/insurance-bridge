"""Versioned deterministic tokenization for Chinese insurance wording."""

import re
import unicodedata

TOKENIZER_VERSION = "cjk-ngram-v1"
_TOKEN_RE = re.compile(r"[\u3400-\u9fff]+|[a-zA-Z]+|[0-9]+(?:[.%％-][0-9]+)*")


def tokenize(text: str) -> list[str]:
    normalized = unicodedata.normalize("NFKC", text).lower()
    tokens: list[str] = []
    for piece in _TOKEN_RE.findall(normalized):
        if "\u3400" <= piece[0] <= "\u9fff":
            if len(piece) <= 3:
                tokens.append(piece)
            else:
                tokens.extend(piece[index : index + 2] for index in range(len(piece) - 1))
                tokens.extend(piece[index : index + 3] for index in range(len(piece) - 2))
        else:
            tokens.append(piece)
    return list(dict.fromkeys(tokens))


def searchable_text(text: str) -> str:
    return " ".join(tokenize(text))
