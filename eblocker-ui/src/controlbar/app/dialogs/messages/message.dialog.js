/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
export default function MessageDialogController($mdDialog, LanguageService, MessageService) {
    'ngInject';

    const vm = this;

    vm.messages = [];

    vm.close = close;

    vm.updateDoNotShowAgain = updateDoNotShowAgain;

    vm.clickedActionButton = clickedActionButton;

    vm.clickedHideButton = clickedHideButton;

    MessageService.getMessages().then(function(response) {
        vm.messages = MessageService.sortMessagesByDate(response);
        vm.hasAlert = MessageService.hasAlertMessage();
        if (vm.hasAlert) {
            vm.messages = MessageService.sortMessagesBySeverity(vm.messages);
        }
        vm.messages.forEach((e)=>{
            e.displayDate = LanguageService.getDate(e.date, 'CONTROLBAR.DIALOGS.MESSAGE.CENTER.DATE_FORMAT');
        });
        vm.messages.reverse(); // to display latest message or alert on top of list
    });

    function close() {
        $mdDialog.cancel();
    }

    function updateDoNotShowAgain(message){
        MessageService.updateDoNotShowAgain(message.id, message.doNotShowAgain);
    }

    function clickedHideButton(message) {
        MessageService.hideMessage(message.id);
    }

    function clickedActionButton(message){
        MessageService.executeAction(message.id);
    }
}
