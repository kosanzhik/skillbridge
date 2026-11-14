package kz.skillbridge.job.remote;

import kz.skillbridge.job.ImportedJob;

/** A fetched posting before filtering: {@code classifyText} is matched against profession keywords. */
public record Candidate(ImportedJob job, String classifyText, boolean openToCountry) {
}
