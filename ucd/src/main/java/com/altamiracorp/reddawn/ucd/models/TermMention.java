package com.altamiracorp.reddawn.ucd.models;

import com.google.gson.annotations.Expose;
import org.json.JSONException;
import org.json.JSONObject;

public class TermMention {
  @Expose
  private final int start;

  @Expose
  private final int end;

  public TermMention(int start, int end) {
    this.start = start;
    this.end = end;
  }

  public TermMention(String str) throws JSONException {
    JSONObject obj = new JSONObject(str);
    this.start = obj.getInt("start");
    this.end = obj.getInt("end");
  }

  public String toString() {
    return String.format("{\"start\":%d,\"end\":%d}", getStart(), getEnd());
  }

  public int getStart() {
    return start;
  }

  public int getEnd() {
    return end;
  }
}
