/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.test

/**
 *
 * @author Kirill Likhodedov
 */
@Mixin(GitExecutor)
class GitLightTest {

  public static final String USER_NAME = "John Doe";
  public static final String USER_EMAIL = "John.Doe@example.com";

  public void setupUsername() {
    git("config user.name $USER_NAME")
    git("config user.email $USER_EMAIL")
  }

}