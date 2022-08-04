package com.zakgof.actr.impl;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.zakgof.actr.Actr;
import com.zakgof.actr.IActorRef;
import com.zakgof.actr.IActorScheduler;
import com.zakgof.actr.IActorSystem;
import com.zakgof.actr.impl.IRegSet.IRegistration;

class ActorImpl<T> implements IActorRef<T> {

    private volatile T object;
    private final ActorSystemImpl actorSystem;
    private final IActorScheduler scheduler;
    private final String name;
    private final BiConsumer<T, Exception> exceptionHandler;
    private final Consumer<T> destructor;
    private volatile Object box;
    private volatile IRegistration reg;

    ActorImpl(T object, Supplier<T> constructor, IActorScheduler scheduler, ActorSystemImpl actorSystem, String name, BiConsumer<T, Exception> exceptionHandler, Consumer<T> destructor) {
        this.actorSystem = actorSystem;
        this.exceptionHandler = exceptionHandler;
        this.name = name == null ? getClass().getSimpleName().toLowerCase() + "_" + UUID.randomUUID() : name;
        this.destructor = destructor;
        if (object != null) {
            this.object = object;
        }
        this.scheduler = scheduler;
        scheduler.actorCreated(this);
        if (constructor != null) {
            this.object = constructor.get();
        }
        actorSystem.add(this);
    }

    @Override
    public void tell(Consumer<T> action) {
        IActorRef<?> caller = Actr.current();
        scheduleCall(action, caller);
    }

    private void scheduleCall(Consumer<T> action, IActorRef<?> caller) {
        scheduleCallErrorAware(action, caller, e -> exceptionHandler.accept(object, e));
    }

    private void scheduleCallErrorAware(Consumer<T> action, IActorRef<?> caller, Consumer<Exception> exceptionCallback) {
        scheduler.schedule(() -> {
            Actr.setCurrent(this);
            Actr.setCaller(caller);
            try {
                if (object == null)
                    return;
                action.accept(object);
            } catch (Exception e) {
                exceptionCallback.accept(e);
            } finally {
                Actr.setCurrent(null);
                Actr.setCaller(null);
            }
        }, this);
    }

    @Override
    public void later(Consumer<T> action, long ms) {
        IActorRef<?> caller = Actr.current();
        actorSystem.later(() -> {
            if (object != null)
                scheduleCall(action, caller);
        }, ms);
    }

    T object() {
        return object;
    }

    @Override
    public String toString() {
        return "[" + name + "]";
    }

    @Override
    public IActorSystem system() {
        return actorSystem;
    }

    /**
     * Called internally from system
     */
    void dispose(Runnable whenFinished) {
        tell(o -> {
            if (destructor != null) {
                try {
                    destructor.accept(object);
                } catch (Exception ex) {
                    ex.printStackTrace(); // TODO: logging
                }
            }
            ((ActorSystemImpl) system()).remove(this);
            scheduler.actorDisposed(this);
            object = null;
            whenFinished.run();
        });
    }

    @Override
    public void close() {
        dispose(() -> {});
    }

    void box(Object box) {
        this.box = box;
    }

    Object box() {
        return box;
    }

    void reg(IRegistration reg) {
        this.reg = reg;
    }

    IRegistration reg() {
        return reg;
    }
}