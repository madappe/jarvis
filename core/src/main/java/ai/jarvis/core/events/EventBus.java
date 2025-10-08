package ai.jarvis.core.events;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Einfacher synchroner Event-Bus. */
public class EventBus {
    private final List<Consumer<Event>> subs = new ArrayList<>();

    public void subscribe(Consumer<Event> consumer) { subs.add(consumer); }

    public void publish(Event event) {
        for (Consumer<Event> s : subs) s.accept(event);
    }
}
