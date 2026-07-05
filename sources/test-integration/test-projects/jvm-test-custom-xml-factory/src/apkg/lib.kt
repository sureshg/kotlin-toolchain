/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package apkg

import java.io.OutputStream
import java.io.Writer
import javax.xml.stream.*
import javax.xml.transform.Result

class OutputFactoryImpl : XMLOutputFactory() {

    private val defaultImpl = newDefaultFactory()

    override fun createXMLStreamWriter(stream: Writer?): XMLStreamWriter? = defaultImpl.createXMLStreamWriter(stream)

    override fun createXMLStreamWriter(stream: OutputStream?): XMLStreamWriter? =
        defaultImpl.createXMLStreamWriter(stream)

    override fun createXMLStreamWriter(
        stream: OutputStream?,
        encoding: String?,
    ): XMLStreamWriter? = defaultImpl.createXMLStreamWriter(stream, encoding)

    override fun createXMLStreamWriter(result: Result?): XMLStreamWriter? = defaultImpl.createXMLStreamWriter(result)

    override fun createXMLEventWriter(result: Result?): XMLEventWriter? = defaultImpl.createXMLEventWriter(result)

    override fun createXMLEventWriter(stream: OutputStream?): XMLEventWriter? = defaultImpl.createXMLEventWriter(stream)

    override fun createXMLEventWriter(
        stream: OutputStream?,
        encoding: String?,
    ): XMLEventWriter? = defaultImpl.createXMLEventWriter(stream, encoding)

    override fun createXMLEventWriter(stream: Writer?): XMLEventWriter? = defaultImpl.createXMLEventWriter(stream)

    override fun setProperty(name: String?, value: Any?): Unit = defaultImpl.setProperty(name, value)

    override fun getProperty(name: String?): Any? = defaultImpl.getProperty(name)

    override fun isPropertySupported(name: String?): Boolean = defaultImpl.isPropertySupported(name)
}