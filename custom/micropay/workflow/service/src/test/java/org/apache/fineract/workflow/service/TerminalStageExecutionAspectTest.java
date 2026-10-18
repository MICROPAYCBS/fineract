/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.workflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TerminalStageExecutionAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private TerminalStageExecution terminalStageExecution;

    @InjectMocks
    private TerminalStageExecutionAspect aspect;

    @Test
    void successfulCommandCompletesArmedTerminalStage() {
        stubProceedResult("approved");

        final Object result = this.aspect.completeTerminalStageAfterCommandSucceeds(this.joinPoint);

        assertEquals("approved", result);
        verify(this.terminalStageExecution).completeIfArmed();
        verify(this.terminalStageExecution, never()).disarm();
    }

    @Test
    void failedExecutionDoesNotCompleteTheTerminalStage() {
        stubProceedFailure(new IllegalStateException("business rule"));

        assertThrows(IllegalStateException.class, () -> this.aspect.completeTerminalStageAfterCommandSucceeds(this.joinPoint));

        verify(this.terminalStageExecution).disarm();
        verify(this.terminalStageExecution, never()).completeIfArmed();
    }

    private void stubProceedResult(final Object result) {
        try {
            when(this.joinPoint.proceed()).thenReturn(result);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubProceedFailure(final RuntimeException failure) {
        try {
            when(this.joinPoint.proceed()).thenThrow(failure);
        } catch (Throwable e) {
            throw new IllegalStateException(e);
        }
    }
}
