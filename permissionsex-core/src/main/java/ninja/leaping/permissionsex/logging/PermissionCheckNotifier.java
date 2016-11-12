/**
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ninja.leaping.permissionsex.logging;

import ninja.leaping.permissionsex.data.SubjectRef;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Delegate to handle notifying of permission and option checks
 */
public interface PermissionCheckNotifier {
    void onPermissionCheck(SubjectRef subject, Set<Map.Entry<String, String>> contexts, String permission, int value);
    void onOptionCheck(SubjectRef subject, Set<Map.Entry<String, String>> contexts, String option, String value);
    void onParentCheck(SubjectRef subject, Set<Map.Entry<String, String>> contexts, List<SubjectRef> parents);

}