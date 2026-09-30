package pt.tiago.cinzeiro.domain.export

import java.io.Writer

/**
 * RFC 4180 CSV, UTF-8 without BOM (encoding is up to whoever opens the `Writer`), separator `,`,
 * line ending `\n`. Fields contain no free text (numbers, ISO-8601, IANA zone ids) → no *quoting*
 * and no risk of formula injection.
 */
object CsvExporter {
    const val HEADER = "id,instant_utc,zone_id,local_datetime"

    /** Writes header + [events] in the order received; returns the number of data rows. */
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
