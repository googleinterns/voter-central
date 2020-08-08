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

angular.module('navigation').component('navBar', {
  templateUrl: '/navigation/nav-bar.template.html',
  controller: function navBarController($scope) {
    addDynamicNavBarContent($scope);
  }
});

async function addDynamicNavBarContent(scope){
  if (location.pathname.includes("directory.html")) {
    scope.isDirectoryPage = true;
    const address = location.search.substring(location.search.indexOf('=') + 1,
    location.search.indexOf('&'));
    const listAllElections =
        location.search.substring(location.search.lastIndexOf('=') + 1);

    // Send GET request to /data with address and whether to list all elections.
    const response = await fetch(`/data?address=${address}&listAllElections=${listAllElections}`);
    const dataPackage = await response.json();

    scope.elections = dataPackage.electionsData;
    scope.$apply();
  }
}
