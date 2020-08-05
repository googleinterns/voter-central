//Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// use modern JavaScript (ES5)
"use strict"

angular.module('navigation').component('header', {
  template: `<a href="/">
               <div class="py-2 mb-3" id="site-header">
                 <h1>
                   <i class="material-icons md-48">how_to_vote</i>
                   Voter Central
                 </h1>
               </div>
             </a>`,
  controller: function headerController() {
    // Generate any dynamic info for header
  }
});
