package com.zakgof.actr;

import java.util.function.Consumer;

/**
 * Interface for addressing actors.
 *
 * @param <T> actor POJO class
 */
public interface IActorRef<T> extends AutoCloseable {

    /**
     * @return actor's @link {ActorSystem}
     */
    IActorSystem system();

    /**
     * Sends a message to the actor defined by this reference.
     *
     * The specified action is executed on the actor's object asynchronously in actor's thread context. This method does not wait for completion of the action, it returns immediately.
     *
     * @param action action to be executed on actor's object.
     */
    void tell(Consumer<T> action);

    /**
     * Schedules an action to be executed once after a specified time.
     *
     * The specified action is executed on the actor's object asynchronously in actor's thread context.
     *
     * @param action action to be executed on actor's object.
     * @param ms delay in milliseconds
     */
    void later(Consumer<T> action, long ms);

    /**
     * Destroy the actor.
     *
     * If defined, destructor will be called in actor's thread context.
     *
     * If actor had a dedicated scheduler, the scheduler will be destroyed as well.
     *
     * Messages pending for this actor will be processed before terminating (TODO ??)
     */
    @Override
	void close();
}
