package io.newblack.eva.elasticsearch

import org.apache.http.impl.client.HttpClients
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.analysis.core.LowerCaseFilter
import org.apache.lucene.analysis.core.WhitespaceTokenizer

class SynonymResourceFactory  {
    fun create(resource: String, format: String): SynonymResource {
        // TODO(kevin): which analyzer should we pass to the synonym parser down the road?
        val analyzer = object : Analyzer() {
            override fun createComponents(fieldName: String?): TokenStreamComponents {
                val tokenizer = WhitespaceTokenizer()
                return TokenStreamComponents(tokenizer, LowerCaseFilter(tokenizer))
            }
        }

        return SynonymResource(true, analyzer, format, resource, HttpClients::createDefault)
    }

}