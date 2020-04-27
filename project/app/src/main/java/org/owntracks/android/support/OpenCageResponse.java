package org.owntracks.android.support;

import androidx.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
class OpenCageResponse {
    private List<OpenCageResult> results;


    public List<OpenCageResult> getResults() {
        return results;
    }

    public @Nullable String getFormatted() {
        return results.size() > 0 ? results.get(0).getFormatted() : null;
    }
}
