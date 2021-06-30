/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

import java.util.function.Consumer;

@ServiceScope(Scopes.BuildTree.class)
public interface IncludedBuildTaskGraph {
    /**
     * Queues a task for execution, but does not schedule it. Use {@link #runScheduledTasks(Consumer)} or {@link IncludedBuildControllers#populateTaskGraphs()} to schedule tasks.
     */
    IncludedBuildTaskResource queueTaskForExecution(BuildIdentifier requestingBuild, BuildIdentifier targetBuild, TaskInternal task);

    /**
     * Queues a task for execution, but does not schedule it. Use {@link #runScheduledTasks(Consumer)} or {@link IncludedBuildControllers#populateTaskGraphs()} to schedule tasks.
     */
    IncludedBuildTaskResource queueTaskForExecution(BuildIdentifier requestingBuild, BuildIdentifier targetBuild, String taskPath);

    /**
     * Schedules and executes queued tasks, collecting any task failures into the given collection.
     */
    void runScheduledTasks(Consumer<? super Throwable> taskFailures);
}
