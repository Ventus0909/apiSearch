package com.ventus.apisearch.index

import com.intellij.util.io.DataExternalizer
import com.ventus.apisearch.model.ApiEndpoint
import com.ventus.apisearch.model.Framework
import java.io.DataInput
import java.io.DataOutput

/**
 * 索引值的序列化格式。字段顺序与版本绑定，修改时需同步递增索引版本。
 */
class ApiEndpointExternalizer : DataExternalizer<List<ApiEndpoint>> {

    override fun save(out: DataOutput, value: List<ApiEndpoint>) {
        out.writeInt(value.size)
        for (endpoint in value) {
            out.writeUTF(endpoint.framework.name)
            out.writeUTF(endpoint.httpMethod)
            out.writeUTF(endpoint.path)
            out.writeUTF(endpoint.className)
            out.writeUTF(endpoint.methodName)
            out.writeUTF(endpoint.comment)
            out.writeUTF(endpoint.filePath)
            out.writeInt(endpoint.offset)
            out.writeNullable(endpoint.feignBaseUrl)
            out.writeNullable(endpoint.feignServiceName)
            out.writeNullable(endpoint.gatewayTarget)
        }
    }

    override fun read(input: DataInput): List<ApiEndpoint> {
        val size = input.readInt()
        val result = ArrayList<ApiEndpoint>(size)
        repeat(size) {
            result.add(
                ApiEndpoint(
                    framework = runCatching { Framework.valueOf(input.readUTF()) }.getOrDefault(Framework.SPRING),
                    httpMethod = input.readUTF(),
                    path = input.readUTF(),
                    className = input.readUTF(),
                    methodName = input.readUTF(),
                    comment = input.readUTF(),
                    filePath = input.readUTF(),
                    offset = input.readInt(),
                    feignBaseUrl = input.readNullable(),
                    feignServiceName = input.readNullable(),
                    gatewayTarget = input.readNullable(),
                )
            )
        }
        return result
    }

    private fun DataOutput.writeNullable(value: String?) {
        if (value == null) {
            writeBoolean(false)
        } else {
            writeBoolean(true)
            writeUTF(value)
        }
    }

    private fun DataInput.readNullable(): String? =
        if (readBoolean()) readUTF() else null
}
