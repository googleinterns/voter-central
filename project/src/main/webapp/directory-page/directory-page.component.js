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

angular.module('directoryPage')
    .component('directoryPage', {
      templateUrl: '/directory-page/directory-page.template.html', 
      controller: function directoryPageController($scope) {
        showOrHideStateFilter($scope);
        getElectionCandidateInformation($scope);
        $scope.filterOnState = function() {
          getElectionCandidateInformation($scope);
        }
        $scope.states =
            ['--', 'AL', 'AK', 'AR', 'AZ', 'CA', 'CO', 'CT', 'DE', 'FL', 'GA',
             'HI', 'IA', 'ID', 'IL', 'IN', 'KS', 'KY', 'LA', 'MA', 'MD', 'ME',
             'MI', 'MN', 'MO', 'MS', 'MT', 'NC', 'ND', 'NE', 'NH', 'NJ', 'NM',
             'NV', 'NY', 'OH', 'OK', 'OR', 'PA', 'RI', 'SC', 'SD', 'TN', 'TX',
             'UT', 'VA', 'VT', 'WA', 'WI', 'WV', 'WY'];
      }
    });

/**
 * Shows the state filter dropdown box if {@code listAllElections} is 'true'.
 * Otherwise hides.
 */
function showOrHideStateFilter(scope) {
  const listAllElections =
      location.search.substring(location.search.lastIndexOf('=') + 1);
  scope.showStateFilter = (listAllElections === 'true');
}

/**
 * Adds a brief version of the official election/candidate information to the
 * page's scope so it can be displayed by the corresponding page template.
 * Queries the backend database with the user input of address.
 */
async function getElectionCandidateInformation(scope) {
  const address = location.search.substring(location.search.indexOf('=') + 1,
      location.search.indexOf('&'));
  const listAllElections =
      location.search.substring(location.search.lastIndexOf('=') + 1);

  // Send GET request to /data with address and whether to list all elections.
  let fetchUrl =
      `/data?address=${address}&listAllElections=${listAllElections}`;
  if (listAllElections === 'true') {
    fetchUrl += `${getStateFilterUrl(scope.stateFilter)}`;
  }
  const response = await fetch(fetchUrl);
  const dataPackage = await response.json();

  scope.elections = dataPackage.electionsData;
  scope.$apply();
}

/**
 * Builds the URL parameter for the user selection value of the state filter.
 * Returns an empty string if the user did not select or if the user selected
 * '--'.
 */
function getStateFilterUrl(stateFilter) {
  return (stateFilter == undefined || stateFilter === '--')
      ? '': `&stateFilter=${stateFilter}`;
}
