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

angular.module('candidatePage').component('candidatePage', {
  templateUrl: '/candidate-page/candidate-page.template.html', 
  controller: function candidatePageController($scope) {
    getCandidateInformation($scope);
  }
});

/**
 * Adds candidate information to the page's scope so it can be
 * inserted by the page template.
 */
async function getCandidateInformation(scope) {
  const candidateId = location.search.substring(
      location.search.indexOf('=') + 1, location.search.indexOf('&'));

  const electionName =
      location.search.substring(location.search.lastIndexOf('=') + 1);
  // Send GET request to /candidate and fetch JSON formatted data for the given
  // candidate ID.
  const response =
      await fetch(
          `/candidate?candidateId=${candidateId}&electionName=${electionName}`);
  const dataPackage = await response.json();

  // Unpack response.
  const officialCandidateInfo = dataPackage.candidateData;
  const newsArticles = dataPackage.newsArticlesData;

  // Add 2 components of information to the scope.
  scope.officialInfo = officialCandidateInfo;
  scope.newsArticles = newsArticles;
  scope.$apply();

  // Then add twitter information and load the widget
  addCandidateTwitterInfo(scope, officialCandidateInfo.twitter)
}


/**
 * Adds the required twitter information into the scope, then
 * loads the script which allows the widget to work.
 */
function addCandidateTwitterInfo(scope, twitterHandle) {
  scope.twitterHandle = twitterHandle;
  scope.twitterURL = "https://twitter.com/" + twitterHandle
      + "?ref_src=twsrc%5Etfw";
  scope.$apply();
  const socialMediaContainer = document.getElementById("social-media-container");
  const twitterScript = document.createElement('script');
  twitterScript.src = "https://platform.twitter.com/widgets.js"
  socialMediaContainer.appendChild(twitterScript);
}