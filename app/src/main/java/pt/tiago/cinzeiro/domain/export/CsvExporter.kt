package pt.tiago.cinzeiro.domain.export

import java.io.Writer

/**
 * CSV RFC 4180, UTF-8 sem BOM (a codificação é de quem abre o `Writer`), separador `,`, fim de
 * linha `\n`. Os campos não têm texto livre (números, ISO-8601, ids de zona IANA) → sem *quoting*
 * nem risco de injecção de fórmulas.
 */
object CsvExporter {
    const val HEADER = "id,instant_utc,zone_id,local_datetime"

    /** Escreve cabeçalho + [events] pela ordem recebida; devolve o n.º de linhas de dados. */
    fun write(events: List<ExportEvent>, out: Writer): Int {
        out.write(HEADER)
        out.write("\n")
        for (e in events) {
            out.write(e.id.toString())
            out.write(",")
            out.write(e.instantUtc)
            out.write(",")
            out.write(e.zone.id)
            out.write(",")
            out.write(e.localDateTime)
            out.write("\n")
        }
        out.flush()
        return events.size
    }
}
