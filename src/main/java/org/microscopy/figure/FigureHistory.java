package org.microscopy.figure;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

/** Bounded settings history. Immutable source pixels are shared, never copied per edit. */
public final class FigureHistory {
  public static final class State {
    public final FigureConfiguration configuration;
    public final InputImageManager inputs;
    public final boolean appearanceInitialized;
    private final String json;
    private State(FigureConfiguration c, InputImageManager i, boolean initialized, Gson gson) {
      json = gson.toJson(c);
      configuration = gson.fromJson(json, FigureConfiguration.class);
      inputs = i.copy(); appearanceInitialized = initialized;
    }
  }
  private final Gson gson = new Gson();
  private final List<State> states = new ArrayList<>();
  private int cursor = -1;
  private String lastGroup;
  private long lastEdit;
  public void record(FigureConfiguration c, InputImageManager inputs, boolean initialized, String group) {
    State state = new State(c, inputs, initialized, gson);
    if (cursor >= 0 && states.get(cursor).json.equals(state.json)
        && states.get(cursor).appearanceInitialized == initialized) return;
    boolean atEnd = cursor == states.size() - 1;
    while (states.size() > cursor + 1) states.remove(states.size() - 1);
    long now = System.nanoTime();
    if (group != null && group.equals(lastGroup) && atEnd && cursor > 0
        && now - lastEdit < 600_000_000L) states.set(cursor, state);
    else { states.add(state); cursor++; }
    if (states.size() > 101) { states.remove(0); cursor--; }
    lastGroup = group; lastEdit = now;
  }
  public void endGroup() { lastGroup = null; }
  public boolean canUndo() { return cursor > 0; }
  public boolean canRedo() { return cursor + 1 < states.size(); }
  public State undo() { if (!canUndo()) return null; endGroup(); return copy(states.get(--cursor)); }
  public State redo() { if (!canRedo()) return null; endGroup(); return copy(states.get(++cursor)); }
  private State copy(State s) { return new State(s.configuration, s.inputs, s.appearanceInitialized, gson); }
}
