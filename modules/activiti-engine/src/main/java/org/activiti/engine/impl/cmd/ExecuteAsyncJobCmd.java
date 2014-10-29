/* Licensed under the Apache License, Version 2.0 (the "License");
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
package org.activiti.engine.impl.cmd;

import java.io.Serializable;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.ActivitiIllegalArgumentException;
import org.activiti.engine.delegate.event.ActivitiEventType;
import org.activiti.engine.delegate.event.impl.ActivitiEventBuilder;
import org.activiti.engine.impl.cfg.TransactionState;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.interceptor.CommandExecutor;
import org.activiti.engine.impl.jobexecutor.FailedJobListener;
import org.activiti.engine.impl.persistence.entity.JobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Tijs Rademakers
 */
public class ExecuteAsyncJobCmd implements Command<Object>, Serializable {

  private static final long serialVersionUID = 1L;

  private static Logger log = LoggerFactory.getLogger(ExecuteAsyncJobCmd.class);
  
  protected JobEntity job;
 
  public ExecuteAsyncJobCmd(JobEntity job) {
  	this.job = job;
  }

  public Object execute(CommandContext commandContext) {
    
    if (job == null) {
      throw new ActivitiIllegalArgumentException("job is null");
    }
    
    if (log.isDebugEnabled()) {
      log.debug("Executing async job {}", job.getId());
    }
    
    try {
      job.execute(commandContext);
      
      if (commandContext.getEventDispatcher().isEnabled()) {
      	commandContext.getEventDispatcher().dispatchEvent(ActivitiEventBuilder.createEntityEvent(
      			ActivitiEventType.JOB_EXECUTION_SUCCESS, job));
      }
      
    } catch (Throwable exception) {
      // When transaction is rolled back, decrement retries
      CommandExecutor commandExecutor = Context
        .getProcessEngineConfiguration()
        .getCommandExecutor();
      
      commandContext.getTransactionContext().addTransactionListener(
        TransactionState.ROLLED_BACK, 
        new FailedJobListener(commandExecutor, job.getId(), exception));
      
      // Dispatch an event, indicating job execution failed in a try-catch block, to prevent the original
      // exception to be swallowed
      if (commandContext.getEventDispatcher().isEnabled()) {
	      try {
	      	commandContext.getEventDispatcher().dispatchEvent(ActivitiEventBuilder.createEntityExceptionEvent(
	      			ActivitiEventType.JOB_EXECUTION_FAILURE, job, exception));
	      } catch(Throwable ignore) {
	      	log.warn("Exception occured while dispatching job failure event, ignoring.", ignore);
	      }
      }
       
      // Finally, Throw the exception to indicate the ExecuteJobCmd failed
      throw new ActivitiException("Job " + job.getId() + " failed", exception);
    }
    return null;
  }
}