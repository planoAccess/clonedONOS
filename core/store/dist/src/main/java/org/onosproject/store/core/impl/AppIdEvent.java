/*
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.store.core.impl;

import org.onosproject.core.ApplicationId;
import org.onosproject.event.AbstractEvent;

/**
 * Application ID event.
 */
public class AppIdEvent extends AbstractEvent<AppIdEvent.Type, ApplicationId> {

    public enum Type {
        APP_REGISTERED
    }

    protected AppIdEvent(Type type, ApplicationId subject) {
        super(type, subject);
    }

}
