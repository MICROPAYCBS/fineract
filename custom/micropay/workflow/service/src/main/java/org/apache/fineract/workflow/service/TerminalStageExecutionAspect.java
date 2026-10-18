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

import static org.apache.fineract.workflow.api.WorkflowApiConstants.MODULE_ENABLED_PROPERTY;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Marks a terminal workflow instance completed only after the held command has been executed.
 *
 * <p>
 * The stage update commits inside processCheckerApprove, which returns before approveEntry runs the command. Completing
 * the instance in that earlier transaction would leave it completed when execution fails and the command is still
 * awaiting approval.
 */
@Aspect
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TerminalStageExecutionAspect {

    private final TerminalStageExecution terminalStageExecution;

    @Around("execution(* org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService+.approveEntry(..))")
    public Object completeTerminalStageAfterCommandSucceeds(final ProceedingJoinPoint joinPoint) {
        final Object result = proceedAndDisarmOnFailure(joinPoint);
        this.terminalStageExecution.completeIfArmed();
        return result;
    }

    private Object proceedAndDisarmOnFailure(final ProceedingJoinPoint joinPoint) {
        try {
            return joinPoint.proceed();
        } catch (RuntimeException e) {
            this.terminalStageExecution.disarm();
            throw e;
        } catch (Throwable e) {
            this.terminalStageExecution.disarm();
            throw new RuntimeException(e);
        }
    }
}
