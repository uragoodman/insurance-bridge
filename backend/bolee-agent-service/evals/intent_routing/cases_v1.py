"""Frozen synthetic intent-routing suite v1. Update by adding a new suite version."""

from __future__ import annotations

from typing import Any


def _call(
    case_id: str,
    intent: str,
    actions: list[str],
    utterance: str,
    *,
    tags: tuple[str, ...] = (),
    history: tuple[dict[str, str], ...] = (),
    required: tuple[str, ...] = (),
    one_of: tuple[tuple[str, ...], ...] = (),
    forbidden: tuple[str, ...] = (),
) -> dict[str, Any]:
    return {
        "case_id": case_id,
        "tags": list(tags),
        "history": list(history),
        "input": utterance,
        "expected": {
            "outcome": "CALL",
            "allowed_intents": [intent],
            "allowed_actions": actions,
            "required_args": list(required),
            "required_one_of": [list(group) for group in one_of],
            "forbidden_args": list(forbidden),
        },
    }


def _no_tool(case_id: str, utterance: str, *, tags: tuple[str, ...] = ()) -> dict[str, Any]:
    return {
        "case_id": case_id,
        "tags": list(tags),
        "history": [],
        "input": utterance,
        "expected": {
            "outcome": "NO_BUSINESS_TOOL",
            "allowed_intents": [],
            "allowed_actions": [],
            "required_args": [],
            "required_one_of": [],
            "forbidden_args": [],
        },
    }


CATALOG = "PRODUCT_CATALOG"
RECOMMEND = "PRODUCT_RECOMMENDATION"
COMPARE = "PRODUCT_COMPARISON"
TERMS = "TERMS_QA"
POLICY = "OWN_POLICY"

CASES: list[dict[str, Any]] = [
    # Product catalog: 10 cases.
    _call(
        "catalog-01",
        CATALOG,
        ["SEARCH_PRODUCTS"],
        "查一下境内旅游保险产品",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "catalog-02",
        CATALOG,
        ["SEARCH_PRODUCTS"],
        "有哪些适合短期出行的意外险？",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "catalog-03",
        CATALOG,
        ["SEARCH_PRODUCTS"],
        "帮我找含医疗保障的旅行险",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "catalog-04",
        CATALOG,
        ["SEARCH_PRODUCTS"],
        "搜索名称里有安心旅行的产品",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "catalog-05",
        CATALOG,
        ["SEARCH_PRODUCTS"],
        "想看看一年期的个人意外险",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "catalog-06",
        CATALOG,
        ["SEARCH_PRODUCTS"],
        "列出适合自驾出行的保险产品",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "catalog-07",
        CATALOG,
        ["SEARCH_PRODUCTS"],
        "有没有覆盖行李损失的旅游险",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "catalog-08",
        CATALOG,
        ["SEARCH_PRODUCTS"],
        "请搜索少儿意外险",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "catalog-09",
        CATALOG,
        ["SEARCH_PRODUCTS"],
        "找一下保障期限为一年的健康险",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "catalog-10",
        CATALOG,
        ["SEARCH_PRODUCTS"],
        "帮我查产品目录中包含住院医疗的产品",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    # Product recommendation: the first step may be category discovery or criteria lookup.
    _call(
        "recommend-01",
        RECOMMEND,
        ["LIST_CATEGORIES", "GET_RECOMMENDATION_CRITERIA"],
        "我想给自己买一份旅游保险，先帮我看看有哪些类型",
        tags=("high_confusion",),
    ),
    _call(
        "recommend-02",
        RECOMMEND,
        ["LIST_CATEGORIES", "GET_RECOMMENDATION_CRITERIA"],
        "根据我的出行需求推荐旅游险，我还没选类别",
    ),
    _call(
        "recommend-03",
        RECOMMEND,
        ["LIST_CATEGORIES", "GET_RECOMMENDATION_CRITERIA"],
        "想给孩子找份意外险，应该怎么选？",
    ),
    _call(
        "recommend-04",
        RECOMMEND,
        ["LIST_CATEGORIES", "GET_RECOMMENDATION_CRITERIA"],
        "我经常出差，推荐合适的保险产品",
    ),
    _call(
        "recommend-05",
        RECOMMEND,
        ["LIST_CATEGORIES", "GET_RECOMMENDATION_CRITERIA"],
        "想买医疗保险，先了解一下可选类别",
    ),
    _call(
        "recommend-06",
        RECOMMEND,
        ["LIST_CATEGORIES", "GET_RECOMMENDATION_CRITERIA"],
        "给老人推荐一份意外险，需要先知道有哪些选择",
    ),
    _call(
        "recommend-07",
        RECOMMEND,
        ["LIST_CATEGORIES", "GET_RECOMMENDATION_CRITERIA"],
        "我准备去欧洲旅行，能按我的情况推荐产品吗",
    ),
    _call(
        "recommend-08",
        RECOMMEND,
        ["LIST_CATEGORIES", "GET_RECOMMENDATION_CRITERIA"],
        "希望给家庭成员配置短期旅行保障，请推荐",
    ),
    _call(
        "recommend-09",
        RECOMMEND,
        ["LIST_CATEGORIES", "GET_RECOMMENDATION_CRITERIA"],
        "我想比较适合学生的意外险选择并得到推荐",
    ),
    _call(
        "recommend-10",
        RECOMMEND,
        ["LIST_CATEGORIES", "GET_RECOMMENDATION_CRITERIA"],
        "计划周末自驾游，帮我推荐保险",
    ),
    # Product comparison starts with a directory search to resolve Java-owned IDs.
    _call(
        "compare-01",
        COMPARE,
        ["SEARCH_PRODUCTS"],
        "比较安心旅行A和安心旅行B这两款产品",
        one_of=(("keyword", "keywords"),),
        tags=("high_confusion",),
    ),
    _call(
        "compare-02",
        COMPARE,
        ["SEARCH_PRODUCTS"],
        "把境内旅游险里价格和医疗保障做个对比",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "compare-03",
        COMPARE,
        ["SEARCH_PRODUCTS"],
        "我想比较两款少儿意外险的保障范围",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "compare-04",
        COMPARE,
        ["SEARCH_PRODUCTS"],
        "对比名称包含安心旅行的产品",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "compare-05",
        COMPARE,
        ["SEARCH_PRODUCTS"],
        "比较短期和一年期意外险有什么产品可选",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "compare-06",
        COMPARE,
        ["SEARCH_PRODUCTS"],
        "比较两款出境旅游保险，重点看医疗保障",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "compare-07",
        COMPARE,
        ["SEARCH_PRODUCTS"],
        "找出适合自驾游的产品后帮我比较",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "compare-08",
        COMPARE,
        ["SEARCH_PRODUCTS"],
        "想知道几款住院医疗险之间的差异",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "compare-09",
        COMPARE,
        ["SEARCH_PRODUCTS"],
        "帮我比较带有行李保障的旅游产品",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    _call(
        "compare-10",
        COMPARE,
        ["SEARCH_PRODUCTS"],
        "把目录里的两款个人意外险对照一下",
        one_of=(("category_id", "keyword", "keywords"),),
    ),
    # Contract / terms Q&A.
    _call(
        "terms-01",
        TERMS,
        ["SEARCH_PRODUCT_TERMS"],
        "平安境内旅游险的航班延误责任，起赔时长是多少？",
        required=("product_name", "question"),
    ),
    _call(
        "terms-02",
        TERMS,
        ["SEARCH_PRODUCT_TERMS"],
        "安心旅行A的免责条款有哪些？",
        required=("product_name", "question"),
    ),
    _call(
        "terms-03",
        TERMS,
        ["SEARCH_PRODUCT_TERMS"],
        "旅游险对行李延误有什么正式保障规定？",
        required=("product_name", "question"),
    ),
    _call(
        "terms-04",
        TERMS,
        ["SEARCH_PRODUCT_TERMS"],
        "某款境内旅行保险的医疗费用责任如何约定？",
        required=("product_name", "question"),
    ),
    _call(
        "terms-05",
        TERMS,
        ["SEARCH_PRODUCT_TERMS"],
        "安心旅行B是否设有等待期？请按条款回答",
        required=("product_name", "question"),
    ),
    _call(
        "terms-06",
        TERMS,
        ["SEARCH_PRODUCT_TERMS"],
        "这个产品对意外身故责任的条款怎么写？",
        history=({"role": "user", "content": "查一下安心旅行A的产品条款。"},),
        required=("product_name", "question"),
    ),
    _call(
        "terms-07",
        TERMS,
        ["SEARCH_PRODUCT_TERMS"],
        "投保前已经发生的疾病在条款中如何处理？",
        required=("product_name", "question"),
    ),
    _call(
        "terms-08",
        TERMS,
        ["SEARCH_PRODUCT_TERMS"],
        "境内旅游险中，紧急救援责任的适用条件是什么？",
        required=("product_name", "question"),
    ),
    _call(
        "terms-09",
        TERMS,
        ["SEARCH_PRODUCT_TERMS"],
        "安心旅行A的航班延误保障有金额上限吗？",
        required=("product_name", "question"),
    ),
    _call(
        "terms-10",
        TERMS,
        ["SEARCH_PRODUCT_TERMS"],
        "这款保险对高风险运动有什么除外约定？",
        history=({"role": "user", "content": "我在看安心旅行B。"},),
        required=("product_name", "question"),
    ),
    # Authenticated user's own policy only.
    _call(
        "policy-01",
        POLICY,
        ["GET_MY_POLICIES"],
        "我名下现在有哪些保单？",
        tags=("high_confusion",),
        forbidden=("policy_id",),
    ),
    _call(
        "policy-02",
        POLICY,
        ["GET_MY_POLICIES"],
        "帮我查本人正在保障中的保单",
        forbidden=("policy_id",),
    ),
    _call("policy-03", POLICY, ["GET_MY_POLICIES"], "我的保单有哪些", forbidden=("policy_id",)),
    _call(
        "policy-04",
        POLICY,
        ["GET_MY_POLICIES"],
        "查询一下我自己名下已生效的保险",
        forbidden=("policy_id",),
    ),
    _call(
        "policy-05", POLICY, ["GET_MY_POLICIES"], "我有哪些待付款的保单？", forbidden=("policy_id",)
    ),
    _call(
        "policy-06",
        POLICY,
        ["GET_MY_POLICIES"],
        "我的保单当前状态是什么？",
        forbidden=("policy_id",),
    ),
    _call(
        "policy-07", POLICY, ["GET_MY_POLICIES"], "列一下我本人买过的保险", forbidden=("policy_id",)
    ),
    _call(
        "policy-08",
        POLICY,
        ["GET_MY_POLICIES"],
        "能查到我名下的旅游险吗？",
        forbidden=("policy_id",),
    ),
    _call(
        "policy-09",
        POLICY,
        ["GET_MY_POLICIES"],
        "请告诉我自己的保单保障日期",
        forbidden=("policy_id",),
    ),
    _call(
        "policy-10",
        POLICY,
        ["GET_MY_POLICIES"],
        "我想查看本人所有保单的产品和状态",
        forbidden=("policy_id",),
    ),
    # Unsupported capabilities must not be disguised as a supported business task.
    _no_tool("unsupported-01", "请帮我提交保险理赔申请", tags=("critical_no_tool",)),
    _no_tool("unsupported-02", "我的理赔处理到哪一步了？", tags=("critical_no_tool",)),
    _no_tool("unsupported-03", "根据我的健康情况判断能不能通过核保", tags=("critical_no_tool",)),
    _no_tool("unsupported-04", "直接帮我购买这份保险并完成投保", tags=("critical_no_tool",)),
    _no_tool("unsupported-05", "帮我退掉当前保险并停止续费", tags=("critical_no_tool",)),
    _no_tool("unsupported-06", "告诉我这款产品的准确报价和最终保费", tags=("critical_no_tool",)),
    _no_tool("unsupported-07", "可以替我修改保单受益人吗？", tags=("critical_no_tool",)),
    _no_tool("unsupported-08", "请替我申请保单贷款", tags=("critical_no_tool",)),
    # Ambiguous, multi-intent, and general knowledge cases: clarify or answer without a tool.
    _no_tool("ambiguous-01", "帮我处理一下保险的事情", tags=("critical_no_tool",)),
    _no_tool("ambiguous-02", "查一下那个产品", tags=("critical_no_tool",)),
    _no_tool(
        "ambiguous-03", "我想咨询保险，先给我推荐产品，再帮我退掉旧保单", tags=("critical_no_tool",)
    ),
    _no_tool("ambiguous-04", "这个和上一个比哪个好？", tags=("critical_no_tool",)),
    _no_tool("ambiguous-05", "保险里的免赔额一般是什么意思？", tags=("critical_no_tool",)),
    _no_tool("ambiguous-06", "你好，你能做什么？", tags=("critical_no_tool",)),
    _no_tool(
        "ambiguous-07", "不记得产品名字，只记得它好像不错，帮我查条款", tags=("critical_no_tool",)
    ),
    _no_tool(
        "ambiguous-08", "我要查我家人的保单，请用他的身份信息查询", tags=("critical_no_tool",)
    ),
    _no_tool(
        "ambiguous-09", "旅游险和意外险都给我推荐，同时把条款也查了", tags=("critical_no_tool",)
    ),
    _no_tool("ambiguous-10", "我这个保单到底能不能赔？", tags=("critical_no_tool",)),
]
