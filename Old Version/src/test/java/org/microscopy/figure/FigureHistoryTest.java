package org.microscopy.figure;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class FigureHistoryTest {
  @Test void layoutAndSharedChannelEditsRoundTripWithoutCopyingPixels() {
    InputImageManager inputs = new InputImageManager();
    FigureConfiguration c = CoreTest.config(inputs, 100, 200);
    FigureHistory history = new FigureHistory();
    history.record(c, inputs, true, null);
    String first = c.conditions.get(0).sourceId;
    c.conditions.remove(0); c.displayChannels.remove(0); c.rowsAreChannels = false;
    c.channel(1).label = "Changed"; c.channel(1).lut = ChannelConfig.Lut.Cyan;
    c.channel(1).min = 5; c.channel(1).invert = true;
    history.record(c, inputs, true, null);
    FigureHistory.State before = history.undo();
    assertEquals(2, before.configuration.conditions.size());
    assertEquals(3, before.configuration.displayChannels.size());
    assertTrue(before.configuration.rowsAreChannels);
    assertEquals("Green", before.configuration.channel(1).label);
    assertSame(inputs.get(first), before.inputs.get(first));
    before.configuration.conditions.clear(); // callers cannot mutate stored history
    FigureHistory.State after = history.redo();
    assertEquals(1, after.configuration.conditions.size());
    assertEquals(ChannelConfig.Lut.Cyan, after.configuration.channel(1).lut);
    assertEquals(5, after.configuration.channel(1).min);
    assertTrue(after.configuration.channel(1).invert);
    assertEquals(2, history.undo().configuration.conditions.size());
  }

  @Test void continuousAdjustmentsCoalesceAndNewEditsDiscardOnlyRedoBranch() {
    InputImageManager inputs = new InputImageManager();
    FigureConfiguration c = CoreTest.config(inputs, 100);
    FigureHistory history = new FigureHistory();
    history.record(c, inputs, true, null);
    c.channel(1).min = 1; history.record(c, inputs, true, "bc:1");
    c.channel(1).min = 2; history.record(c, inputs, true, "bc:1");
    FigureHistory.State before = history.undo();
    assertEquals(0, before.configuration.channel(1).min);
    assertFalse(history.canUndo()); assertTrue(history.canRedo());
    before.configuration.conditions.get(0).label = "Branch";
    history.record(before.configuration, before.inputs, true, null);
    assertFalse(history.canRedo());
    assertEquals("C100", history.undo().configuration.conditions.get(0).label);
  }

  @Test void undoLastRemovalAndFirstAdditionRestoresInitializationState() {
    FigureHistory h = new FigureHistory();
    InputImageManager inputs = new InputImageManager();
    h.record(new FigureConfiguration(), inputs, false, null);
    FigureConfiguration c = CoreTest.config(inputs, 1);
    h.record(c, inputs, true, null);
    c.conditions.clear(); c.displayChannels.clear(); h.record(c, inputs, true, null);
    assertEquals(1, h.undo().configuration.conditions.size());
    FigureHistory.State empty = h.undo();
    assertFalse(empty.appearanceInitialized); assertTrue(empty.configuration.conditions.isEmpty());
    assertEquals(1, h.redo().configuration.conditions.size());
  }
}
