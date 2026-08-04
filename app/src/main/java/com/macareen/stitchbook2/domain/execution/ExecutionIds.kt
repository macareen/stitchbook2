package com.macareen.stitchbook2.domain.execution

@JvmInline
value class GuideId(val value: String) {
    init {
        require(value.isNotBlank()) { "Guide ID must not be blank." }
    }
}

@JvmInline
value class DefinitionRevisionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Definition revision ID must not be blank." }
    }
}

@JvmInline
value class NodeId(val value: String) {
    init {
        require(value.isNotBlank()) { "Node ID must not be blank." }
    }
}

@JvmInline
value class ExecutionId(val value: String) {
    init {
        require(value.isNotBlank()) { "Execution ID must not be blank." }
    }
}
