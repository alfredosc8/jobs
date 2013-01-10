package de.otto.jobstore.common;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement
public final class RemoteJobStatus {

    public static enum Status {
        RUNNING,
        FINISHED
    }

    public Status status;

    @XmlElement(name = "log_lines")
    public List<String> logLines = new ArrayList<>();

    public RemoteJobResult result;

    @XmlElement(name = "finish_time")
    public String finishTime;

    public String message;

    public RemoteJobStatus() {
    }

    public RemoteJobStatus(Status status, List<String> logLines, RemoteJobResult result, String finishTime) {
        this.status = status;
        this.logLines = logLines;
        this.result = result;
        this.finishTime = finishTime;
    }

    public RemoteJobStatus(Status status, List<String> logLines, String message) {
        this.status = status;
        this.logLines = logLines;
        this.message = message;
    }
}
