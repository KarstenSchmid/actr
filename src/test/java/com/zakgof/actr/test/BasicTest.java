package com.zakgof.actr.test;

import static com.zakgof.actr.test.BasicTest.CheckPoints.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.zakgof.actr.Actr;
import com.zakgof.actr.IActorRef;
import com.zakgof.actr.IActorSystem;
import com.zakgof.actr.Schedulers;

class BasicTest {

    private final IActorSystem system = Actr.newSystem("test", Schedulers.newThreadPerActorScheduler());

    private final IActorRef<Master> master = system.<Master>actorBuilder()
            .constructor(Master::new)
            .build();
    private IActorRef<TestActor> testActor;

    @BeforeEach
    void before() {
        CheckPoints.clean();

        testActor = system.<TestActor>actorBuilder()
                .constructor(TestActor::new)
                .destructor(TestActor::destructor)
                .exceptionHandler(TestActor::err)
                .build();
    }

    @Test
    void tell() {
        testActor.tell(TestActor::simple);
        system.shutdownCompletable().join();
        validate(ActorConstructor, ActorCallSimple, ActorDestructor);
    }

    @Test
    void tellError() {
        master.tell(Master::tellError);
        system.shutdownCompletable().join();
        validate(ActorConstructor, ActorCallThrowing, ExceptionHandler, ActorDestructor);
    }

    private class Master {

        void tellError() {
            testActor.tell(TestActor::throwing);
            assertNull(Actr.caller());
            assertEquals(master, Actr.current());
        }

        private void validateResult(int result) {
            assertEquals(47, result);
            assertEquals(master, Actr.current());
            assertEquals(testActor, Actr.caller());
            ResultReturned.check();
            system.shutdown();
        }
    }

    private class TestActor {

        TestActor() {
            assertEquals(testActor, Actr.current());
            ActorConstructor.check();
        }

        void simple() {
            assertEquals(testActor, Actr.current());
            ActorCallSimple.check();
            system.shutdown();
        }

        int returning() {
            assertEquals(testActor, Actr.current());
            CheckPoints.ActorCallReturning.check();
            return 47;
        }

        int throwing() {
            CheckPoints.ActorCallThrowing.check();
            throw new RuntimeException("oops");
        }

        void err(Exception e) {
            assertEquals(testActor, Actr.current());
            ExceptionHandler.check();
            system.shutdown();
        }

        void destructor() {
            assertEquals(testActor, Actr.current());
            ActorDestructor.check();
        }

    }

    enum CheckPoints {
        ActorConstructor,
        ActorCallSimple,
        ActorCallReturning,
        ActorCallThrowing,
        ActorDestructor,
        ResultReturned,
        ExceptionHandler,
        FutureCompleted,
        FutureFailed,
        NonActorCallback;

        private static List<CheckPoints> checkpoints = new ArrayList<>();

        static void clean() {
            checkpoints.clear();
        }

        void check() {
            checkpoints.add(this);
        }

        static void validate(CheckPoints... reference) {
            assertEquals(Arrays.asList(reference), checkpoints);
        }
    }

}
